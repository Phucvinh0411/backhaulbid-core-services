package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.InsuranceInfo;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.CargoLiabilityDtos;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.InsuranceInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static iuh.fit.se.domain.enums.InsuranceCoverageType.CARGO_LIABILITY;

/**
 * A carrier submits a cargo-liability certificate; an admin reviews it. Verification is never automatic: legacy rows
 * are not reviewable and cannot become VERIFIED without a reviewer. An expired certificate is only marked EXPIRED. It
 * does not lock the account.
 */
@Service
@RequiredArgsConstructor
public class CargoLiabilityService {

    private final CompanyRepository companyRepository;
    private final InsuranceInfoRepository insuranceRepository;
    private final MediaCertificateClient mediaCertificateClient;

    @Transactional
    public CargoLiabilityDtos.Submission submit(String accountId, CargoLiabilityDtos.SubmitRequest request) {
        Company company = companyOf(accountId)
                .orElseThrow(() -> new IllegalStateException(
                        "Vui lòng hoàn tất xác minh doanh nghiệp trước khi gửi chứng từ bảo hiểm"));
        if (!CargoLiabilityRules.isCertificateKey(request.certificateKey())) {
            throw new IllegalArgumentException("Tệp chứng từ bảo hiểm không hợp lệ. Vui lòng tải lên lại.");
        }
        if (request.expiredDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày hết hạn bảo hiểm phải từ hôm nay trở đi");
        }
        if (insuranceRepository.existsByCompany_IdAndCoverageTypeAndStatus(
                company.getId(), CARGO_LIABILITY, VerificationStatus.PENDING)) {
            throw new IllegalStateException(
                    "Đã có chứng từ đang chờ xét duyệt. Vui lòng đợi kết quả trước khi gửi lại.");
        }

        InsuranceInfo row = InsuranceInfo.builder()
                .company(company)
                .providerName(request.providerName().trim())
                .policyNumber(request.policyNumber().trim())
                .coverageLimit(request.coverageLimit())
                .expiredDate(request.expiredDate())
                .coverageType(CARGO_LIABILITY)
                .certificateKey(request.certificateKey())
                .status(VerificationStatus.PENDING)
                .submittedAt(Instant.now())
                .build();
        return toSubmission(insuranceRepository.save(row));
    }

    /** Also marks stale VERIFIED rows EXPIRED. Eligibility itself is decided from the dates, not from this mark. */
    @Transactional
    public CargoLiabilityDtos.Status current(String accountId) {
        Optional<Company> company = companyOf(accountId);
        if (company.isEmpty()) {
            return new CargoLiabilityDtos.Status(false, null, null);
        }
        UUID companyId = company.get().getId();
        LocalDate today = LocalDate.now();
        List<InsuranceInfo> rows = insuranceRepository.findAllByCompany_IdAndCoverageType(companyId, CARGO_LIABILITY);
        rows.forEach(row -> markExpired(row, today));

        LocalDate verifiedUntil = CargoLiabilityRules.latestQualifyingExpiry(rows, today);
        CargoLiabilityDtos.Submission latest = insuranceRepository
                .findFirstByCompany_IdAndCoverageTypeOrderBySubmittedAtDesc(companyId, CARGO_LIABILITY)
                .map(this::toSubmission)
                .orElse(null);
        return new CargoLiabilityDtos.Status(verifiedUntil != null, verifiedUntil, latest);
    }

    @Transactional(readOnly = true)
    public boolean isEligible(UUID accountId) {
        return companyRepository.findByAccount_Id(accountId)
                .map(company -> CargoLiabilityRules.latestQualifyingExpiry(
                        insuranceRepository.findAllByCompany_IdAndCoverageType(company.getId(), CARGO_LIABILITY),
                        LocalDate.now()) != null)
                .orElse(false);
    }

    @Transactional
    public CargoLiabilityDtos.ReviewPage findForReview(String status, int page, int pageSize) {
        VerificationStatus verificationStatus = parseStatus(status);
        Page<InsuranceInfo> rows = insuranceRepository.findAllByCoverageTypeAndStatus(
                CARGO_LIABILITY,
                verificationStatus,
                PageRequest.of(page, pageSize, Sort.by(Sort.Direction.ASC, "submittedAt")));
        LocalDate today = LocalDate.now();
        rows.getContent().forEach(row -> markExpired(row, today));
        return new CargoLiabilityDtos.ReviewPage(
                rows.getContent().stream().map(this::toReviewItem).toList(),
                page,
                pageSize,
                rows.getTotalElements(),
                rows.getTotalPages());
    }

    @Transactional
    public CargoLiabilityDtos.Submission review(UUID id, String adminId, String decision, String rejectionReason) {
        InsuranceInfo row = reviewable(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ chứng từ bảo hiểm"));
        if (row.getStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Hồ sơ này đã được xét duyệt");
        }

        if ("APPROVE".equals(decision)) {
            if (row.getExpiredDate() == null || row.getExpiredDate().isBefore(LocalDate.now())) {
                throw new IllegalStateException("Chứng từ đã hết hạn nên không thể xác minh");
            }
            row.setStatus(VerificationStatus.VERIFIED);
            row.setRejectionReason(null);
        } else if ("REJECT".equals(decision)) {
            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new IllegalArgumentException("Vui lòng nhập lý do từ chối chứng từ");
            }
            row.setStatus(VerificationStatus.REJECTED);
            row.setRejectionReason(rejectionReason.trim());
        } else {
            throw new IllegalArgumentException("Quyết định phải là APPROVE hoặc REJECT");
        }
        row.setVerifiedBy(UUID.fromString(adminId));
        row.setVerifiedAt(Instant.now());
        return toSubmission(insuranceRepository.save(row));
    }

    /** Admin-only at the controller. Streams the private file through media-service; no public URL is ever built. */
    public MediaCertificateClient.Certificate certificate(UUID id) {
        InsuranceInfo row = reviewable(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ chứng từ bảo hiểm"));
        return mediaCertificateClient.fetch(row.getCertificateKey());
    }

    private Optional<Company> companyOf(String accountId) {
        return companyRepository.findByAccount_Id(UUID.fromString(accountId));
    }

    /** Legacy rows have no certificate file, so they are never reviewable and never shown as a certificate. */
    private Optional<InsuranceInfo> reviewable(UUID id) {
        return insuranceRepository.findById(id)
                .filter(row -> row.getCoverageType() == CARGO_LIABILITY && row.getCertificateKey() != null);
    }

    private static void markExpired(InsuranceInfo row, LocalDate today) {
        if (row.getStatus() == VerificationStatus.VERIFIED
                && row.getExpiredDate() != null
                && row.getExpiredDate().isBefore(today)) {
            row.setStatus(VerificationStatus.EXPIRED);
        }
    }

    private static VerificationStatus parseStatus(String status) {
        try {
            return VerificationStatus.valueOf(status);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Trạng thái hồ sơ không hợp lệ");
        }
    }

    private CargoLiabilityDtos.Submission toSubmission(InsuranceInfo row) {
        return new CargoLiabilityDtos.Submission(
                row.getId(),
                row.getStatus() == null ? null : row.getStatus().name(),
                row.getProviderName(),
                row.getPolicyNumber(),
                row.getCoverageLimit(),
                row.getExpiredDate(),
                row.getSubmittedAt(),
                row.getRejectionReason());
    }

    private CargoLiabilityDtos.ReviewItem toReviewItem(InsuranceInfo row) {
        Company company = row.getCompany();
        return new CargoLiabilityDtos.ReviewItem(
                row.getId(),
                company.getAccount().getId(),
                company.getCompanyName(),
                company.getTaxCode(),
                row.getProviderName(),
                row.getPolicyNumber(),
                row.getCoverageLimit(),
                row.getExpiredDate(),
                row.getSubmittedAt(),
                row.getStatus() == null ? null : row.getStatus().name(),
                row.getRejectionReason());
    }
}
