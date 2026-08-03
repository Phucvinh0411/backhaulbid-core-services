package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.CompanyStatus;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.BusinessLookupResponse;
import iuh.fit.se.dto.BusinessVerificationResponse;
import iuh.fit.se.dto.BusinessVerificationPageResponse;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyVerificationService {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final BusinessVerificationService businessVerificationService;
    private final BusinessLicenseStorage businessLicenseStorage;
    private final BusinessVerificationMapper businessVerificationMapper;
    private final EkycVerificationRepository ekycVerificationRepository;

    @Transactional
    public BusinessVerificationResponse submit(
            String accountId,
            String taxCode,
            String ekycRepresentativeName,
            MultipartFile businessLicense,
            MultipartFile authorizationLetter) {
        UUID parsedAccountId = UUID.fromString(accountId);
        Account account = accountRepository.findById(parsedAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));
        String normalizedTaxCode = taxCode == null ? "" : taxCode.trim();
        if (!normalizedTaxCode.matches("\\d{10}(\\d{3})?")) {
            throw new IllegalArgumentException("Mã số thuế phải gồm 10 hoặc 13 chữ số");
        }
        if (companyRepository.existsByTaxCodeAndAccount_IdNot(
                normalizedTaxCode, parsedAccountId)) {
            throw new IllegalArgumentException(
                    "Mã số thuế đã được sử dụng bởi tài khoản khác");
        }

        BusinessLookupResponse lookup =
                businessVerificationService.lookupByTaxCode(normalizedTaxCode);
        if (!lookup.isValid()) {
            throw new IllegalArgumentException(
                    "Không thể xác minh doanh nghiệp từ mã số thuế đã nhập");
        }

        EkycVerification ekycVerification = ekycVerificationRepository
                .findByAccount_Id(parsedAccountId)
                .filter(verification -> verification.getStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new IllegalStateException(
                        "Vui lòng eKYC người đại diện trước khi xác minh doanh nghiệp"));
        String normalizedEkycRepresentativeName = firstNonBlank(
                ekycVerification.getFullName(), ekycRepresentativeName);
        boolean representativeMatched = representativeNamesMatch(
                normalizedEkycRepresentativeName, lookup.getRepresentative());
        boolean requiresAuthorization = !representativeMatched;
        if (requiresAuthorization
                && (authorizationLetter == null || authorizationLetter.isEmpty())) {
            throw new IllegalArgumentException(
                    "Người đại diện doanh nghiệp không trùng eKYC. Vui lòng nộp giấy ủy quyền");
        }

        Optional<Company> existingCompany =
                companyRepository.findByAccount_Id(parsedAccountId);
        existingCompany
                .map(Company::getVerificationStatus)
                .filter(status -> status == VerificationStatus.PENDING
                        || status == VerificationStatus.VERIFIED)
                .ifPresent(status -> {
                    throw new IllegalStateException(
                            status == VerificationStatus.PENDING
                                    ? "Hồ sơ doanh nghiệp đang chờ xét duyệt"
                                    : "Doanh nghiệp đã được xác minh");
                });

        BusinessLicenseStorage.StoredBusinessLicense storedLicense =
                businessLicenseStorage.store(businessLicense);
        BusinessLicenseStorage.StoredBusinessLicense storedAuthorizationLetter =
                requiresAuthorization
                        ? businessLicenseStorage.store(authorizationLetter)
                        : null;
        Company company = existingCompany
                .orElseGet(() -> Company.builder().account(account).build());
        company.setTaxCode(normalizedTaxCode);
        company.setCompanyName(limitLength(lookup.getCompanyName(), 255));
        company.setAddress(limitLength(lookup.getAddress(), 255));
        company.setLegalRepresentative(limitLength(
                lookup.getRepresentative(), 255));
        company.setEkycRepresentativeName(limitLength(
                normalizedEkycRepresentativeName, 255));
        company.setRepresentativeMatched(representativeMatched);
        company.setRequiresAuthorization(requiresAuthorization);
        company.setBusinessLicenseUrl(storedLicense.storedFilename());
        company.setBusinessLicenseFilename(storedLicense.originalFilename());
        company.setAuthorizationLetterUrl(
                storedAuthorizationLetter == null
                        ? null
                        : storedAuthorizationLetter.storedFilename());
        company.setAuthorizationLetterFilename(
                storedAuthorizationLetter == null
                        ? null
                        : storedAuthorizationLetter.originalFilename());
        company.setVerificationStatus(VerificationStatus.PENDING);
        company.setCompanyStatus(CompanyStatus.ACTIVE);
        company.setRejectionReason(null);

        return businessVerificationMapper.toResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public Optional<BusinessVerificationResponse> findCurrent(String accountId) {
        return companyRepository.findByAccount_Id(UUID.fromString(accountId))
                .map(businessVerificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BusinessVerificationPageResponse findByStatus(
            String status,
            int page,
            int pageSize) {
        VerificationStatus verificationStatus;
        try {
            verificationStatus = VerificationStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Trạng thái hồ sơ không hợp lệ");
        }
        Page<Company> companies = companyRepository.findAllByVerificationStatus(
                verificationStatus,
                PageRequest.of(
                        page,
                        pageSize,
                        Sort.by(Sort.Direction.ASC, "companyName")));
        return BusinessVerificationPageResponse.builder()
                .items(companies.getContent().stream()
                        .map(businessVerificationMapper::toResponse)
                        .toList())
                .page(page)
                .pageSize(pageSize)
                .totalItems(companies.getTotalElements())
                .totalPages(companies.getTotalPages())
                .build();
    }

    @Transactional
    public BusinessVerificationResponse review(
            UUID verificationId,
            String decision,
            String rejectionReason) {
        Company company = companyRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ doanh nghiệp"));
        if (company.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Hồ sơ này đã được xét duyệt");
        }

        if ("APPROVE".equals(decision)) {
            company.setVerificationStatus(VerificationStatus.VERIFIED);
            company.setRejectionReason(null);
        } else if ("REJECT".equals(decision)) {
            if (rejectionReason == null || rejectionReason.isBlank()) {
                throw new IllegalArgumentException(
                        "Vui lòng nhập lý do từ chối hồ sơ");
            }
            company.setVerificationStatus(VerificationStatus.REJECTED);
            company.setRejectionReason(rejectionReason.trim());
        } else {
            throw new IllegalArgumentException(
                    "Quyết định phải là APPROVE hoặc REJECT");
        }

        return businessVerificationMapper.toResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public BusinessDocumentDownload getDocument(
            UUID verificationId,
            String documentType) {
        Company company = companyRepository.findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ doanh nghiệp"));
        boolean authorizationLetter =
                "authorizationLetter".equals(documentType);
        String storedFilename = authorizationLetter
                ? company.getAuthorizationLetterUrl()
                : company.getBusinessLicenseUrl();
        if (storedFilename == null) {
            throw new IllegalArgumentException(
                    authorizationLetter
                            ? "Hồ sơ chưa có giấy ủy quyền người đại diện"
                            : "Hồ sơ chưa có giấy phép đăng ký kinh doanh");
        }
        MediaType mediaType = storedFilename.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : storedFilename.endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;
        String originalFilename = authorizationLetter
                ? firstNonBlank(
                        company.getAuthorizationLetterFilename(),
                        "giay-uy-quyen")
                : firstNonBlank(
                        company.getBusinessLicenseFilename(),
                        "giay-phep-doanh-nghiep");
        return new BusinessDocumentDownload(
                businessLicenseStorage.load(storedFilename),
                originalFilename,
                mediaType
        );
    }

    public record BusinessDocumentDownload(
            Resource resource,
            String filename,
            MediaType mediaType) {
    }

    private String limitLength(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private boolean representativeNamesMatch(
            String ekycRepresentativeName,
            String businessRepresentativeName) {
        String normalizedEkycName = normalizeRepresentativeName(ekycRepresentativeName);
        String normalizedBusinessName = normalizeRepresentativeName(businessRepresentativeName);
        return !normalizedEkycName.isBlank()
                && normalizedEkycName.equals(normalizedBusinessName);
    }

    private String normalizeRepresentativeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("[^A-Za-z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
