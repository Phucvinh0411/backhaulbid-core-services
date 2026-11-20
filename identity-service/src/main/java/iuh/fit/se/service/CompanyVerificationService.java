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
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CompanyVerificationService {

    public record BusinessDocument(byte[] bytes, String contentType, String fileName) {
    }

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern LICENSE_KEY_OR_URL = Pattern.compile(
            "^(?:https?://[^\\s?#]{1,400}/)?(business-verifications/licenses/" + UUID_PATTERN + "\\.(?:png|jpe?g|webp|gif|pdf))$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH_LETTER_KEY_OR_URL = Pattern.compile(
            "^(?:https?://[^\\s?#]{1,400}/)?(business-verifications/authorization/" + UUID_PATTERN + "\\.(?:png|jpe?g|webp|gif|pdf))$",
            Pattern.CASE_INSENSITIVE);

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final BusinessVerificationService businessVerificationService;
    private final BusinessVerificationMapper businessVerificationMapper;
    private final EkycVerificationRepository ekycVerificationRepository;
    private final EkycReviewService ekycReviews;
    private final MediaCertificateClient mediaCertificateClient;

    @Transactional
    public BusinessVerificationResponse submit(
            String accountId,
            String taxCode,
            String ekycRepresentativeName,
            String businessLicenseUrl,
            String authorizationLetterUrl) {
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
                && (authorizationLetterUrl == null || authorizationLetterUrl.trim().isEmpty())) {
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
        company.setBusinessLicenseUrl(businessLicenseUrl);
        company.setBusinessLicenseFilename(null);
        company.setAuthorizationLetterUrl(authorizationLetterUrl);
        company.setAuthorizationLetterFilename(null);
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
                        .map(company -> withRepresentativeEkyc(
                                businessVerificationMapper.toResponse(company), company))
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

        return withRepresentativeEkyc(businessVerificationMapper.toResponse(companyRepository.save(company)), company);
    }

    @Transactional(readOnly = true)
    public BusinessDocument document(UUID verificationId, String documentType) {
        Company company = companyRepository.findById(verificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ doanh nghiệp"));
        String rawValue;
        Pattern pattern;
        String baseName;
        if ("businessLicense".equals(documentType)) {
            rawValue = company.getBusinessLicenseUrl();
            pattern = LICENSE_KEY_OR_URL;
            baseName = "giay-phep-doanh-nghiep";
        } else if ("authorizationLetter".equals(documentType)) {
            rawValue = company.getAuthorizationLetterUrl();
            pattern = AUTH_LETTER_KEY_OR_URL;
            baseName = "giay-uy-quyen";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại tài liệu không hợp lệ");
        }
        String key = extractDocumentKey(rawValue, pattern);
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài liệu doanh nghiệp");
        }
        MediaCertificateClient.Certificate fetched = mediaCertificateClient.fetch(key);
        String ext = key.substring(key.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return new BusinessDocument(fetched.bytes(), fetched.contentType(), baseName + ext);
    }

    static String extractDocumentKey(String rawValue, Pattern pattern) {
        if (rawValue == null || rawValue.isBlank()) return null;
        var matcher = pattern.matcher(rawValue.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** Attaches the owner's newest eKYC report for the admin review. Owner-facing responses never carry it. */
    private BusinessVerificationResponse withRepresentativeEkyc(BusinessVerificationResponse response, Company company) {
        response.setEkycRepresentative(
                ekycReviews.latestRepresentative(company.getAccount().getId()).orElse(null));
        return response;
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
