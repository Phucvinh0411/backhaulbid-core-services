package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.BusinessLookupResponse;
import iuh.fit.se.dto.BusinessVerificationResponse;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyVerificationServiceTest {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("8f14e45f-ea43-4a4f-b716-3f8f68f74201");

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private BusinessVerificationService businessVerificationService;
    @Mock
    private BusinessLicenseStorage businessLicenseStorage;
    @Mock
    private EkycVerificationRepository ekycVerificationRepository;
    @Spy
    private BusinessVerificationMapper businessVerificationMapper =
            Mappers.getMapper(BusinessVerificationMapper.class);
    @InjectMocks
    private CompanyVerificationService companyVerificationService;

    @Test
    void submit_validLookupAndDocument_persistsPendingCompany() {
        Account account = Account.builder().id(ACCOUNT_ID).build();
        MockMultipartFile license = new MockMultipartFile(
                "businessLicense", "license.pdf", "application/pdf", "%PDF".getBytes());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(companyRepository.existsByTaxCodeAndAccount_IdNot(
                "0312345678", ACCOUNT_ID)).thenReturn(false);
        when(businessVerificationService.lookupByTaxCode("0312345678"))
                .thenReturn(BusinessLookupResponse.builder()
                        .taxCode("0312345678")
                        .companyName("BackHaulBid Logistics")
                        .address("TP.HCM")
                        .representative("Nguyễn Văn A")
                        .valid(true)
                        .build());
        when(ekycVerificationRepository.findByAccount_Id(ACCOUNT_ID))
                .thenReturn(Optional.of(EkycVerification.builder()
                        .fullName("Nguyễn Văn A")
                        .status(VerificationStatus.VERIFIED)
                        .build()));
        when(businessLicenseStorage.store(license))
                .thenReturn(new BusinessLicenseStorage.StoredBusinessLicense(
                        "generated.pdf", "license.pdf"));
        when(companyRepository.findByAccount_Id(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"));
            return company;
        });

        BusinessVerificationResponse result = companyVerificationService.submit(
                ACCOUNT_ID.toString(), "0312345678", null, license, null);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getCompanyName()).isEqualTo("BackHaulBid Logistics");
        assertThat(result.getBusinessLicenseFilename()).isEqualTo("license.pdf");
        assertThat(result.getEkycRepresentativeName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.getRepresentativeMatched()).isTrue();
        assertThat(result.getRequiresAuthorization()).isFalse();
    }

    @Test
    void submit_representativeMismatchWithoutAuthorization_isRejected() {
        Account account = Account.builder().id(ACCOUNT_ID).build();
        MockMultipartFile license = new MockMultipartFile(
                "businessLicense", "license.pdf", "application/pdf", "%PDF".getBytes());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(companyRepository.existsByTaxCodeAndAccount_IdNot(
                "0312345678", ACCOUNT_ID)).thenReturn(false);
        when(businessVerificationService.lookupByTaxCode("0312345678"))
                .thenReturn(BusinessLookupResponse.builder()
                        .taxCode("0312345678")
                        .companyName("BackHaulBid Logistics")
                        .address("TP.HCM")
                        .representative("Nguyễn Văn A")
                        .valid(true)
                        .build());
        when(ekycVerificationRepository.findByAccount_Id(ACCOUNT_ID))
                .thenReturn(Optional.of(EkycVerification.builder()
                        .fullName("Trần Văn B")
                        .status(VerificationStatus.VERIFIED)
                        .build()));

        assertThatThrownBy(() -> companyVerificationService.submit(
                ACCOUNT_ID.toString(), "0312345678", null, license, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giấy ủy quyền");
        verify(businessLicenseStorage, never()).store(any());
        verify(companyRepository, never()).save(any());
    }

    @Test
    void submit_representativeMismatchWithAuthorization_persistsAuthorization() {
        Account account = Account.builder().id(ACCOUNT_ID).build();
        MockMultipartFile license = new MockMultipartFile(
                "businessLicense", "license.pdf", "application/pdf", "%PDF".getBytes());
        MockMultipartFile authorization = new MockMultipartFile(
                "authorizationLetter", "uy-quyen.pdf", "application/pdf", "%PDF".getBytes());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(companyRepository.existsByTaxCodeAndAccount_IdNot(
                "0312345678", ACCOUNT_ID)).thenReturn(false);
        when(businessVerificationService.lookupByTaxCode("0312345678"))
                .thenReturn(BusinessLookupResponse.builder()
                        .taxCode("0312345678")
                        .companyName("BackHaulBid Logistics")
                        .address("TP.HCM")
                        .representative("Nguyễn Văn A")
                        .valid(true)
                        .build());
        when(ekycVerificationRepository.findByAccount_Id(ACCOUNT_ID))
                .thenReturn(Optional.of(EkycVerification.builder()
                        .fullName("Trần Văn B")
                        .status(VerificationStatus.VERIFIED)
                        .build()));
        when(businessLicenseStorage.store(license))
                .thenReturn(new BusinessLicenseStorage.StoredBusinessLicense(
                        "generated-license.pdf", "license.pdf"));
        when(businessLicenseStorage.store(authorization))
                .thenReturn(new BusinessLicenseStorage.StoredBusinessLicense(
                        "generated-authorization.pdf", "uy-quyen.pdf"));
        when(companyRepository.findByAccount_Id(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"));
            return company;
        });

        BusinessVerificationResponse result = companyVerificationService.submit(
                ACCOUNT_ID.toString(), "0312345678", null, license, authorization);

        assertThat(result.getRepresentativeMatched()).isFalse();
        assertThat(result.getRequiresAuthorization()).isTrue();
        assertThat(result.getAuthorizationLetterFilename()).isEqualTo("uy-quyen.pdf");
    }

    @Test
    void submit_lookupNotValid_doesNotStoreDocument() {
        Account account = Account.builder().id(ACCOUNT_ID).build();
        MockMultipartFile license = new MockMultipartFile(
                "businessLicense", "license.pdf", "application/pdf", "%PDF".getBytes());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(companyRepository.existsByTaxCodeAndAccount_IdNot(
                "0312345678", ACCOUNT_ID)).thenReturn(false);
        when(businessVerificationService.lookupByTaxCode("0312345678"))
                .thenReturn(BusinessLookupResponse.builder().valid(false).build());

        assertThatThrownBy(() -> companyVerificationService.submit(
                ACCOUNT_ID.toString(), "0312345678", null, license, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể xác minh doanh nghiệp");
        verify(businessLicenseStorage, never()).store(any());
        verify(companyRepository, never()).save(any());
    }

    @Test
    void findCurrent_existingSubmission_returnsPersistedStatus() {
        Company company = Company.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .taxCode("0312345678")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(companyRepository.findByAccount_Id(ACCOUNT_ID))
                .thenReturn(Optional.of(company));

        Optional<BusinessVerificationResponse> result =
                companyVerificationService.findCurrent(ACCOUNT_ID.toString());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void review_pendingSubmission_approvesCompany() {
        UUID verificationId =
                UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d");
        Company company = Company.builder()
                .id(verificationId)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(companyRepository.findById(verificationId))
                .thenReturn(Optional.of(company));
        when(companyRepository.save(company)).thenReturn(company);

        BusinessVerificationResponse result = companyVerificationService.review(
                verificationId, "APPROVE", null);

        assertThat(result.getStatus()).isEqualTo("VERIFIED");
        assertThat(company.getRejectionReason()).isNull();
    }

    @Test
    void review_rejectionWithoutReason_isRejected() {
        UUID verificationId =
                UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d");
        Company company = Company.builder()
                .id(verificationId)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(companyRepository.findById(verificationId))
                .thenReturn(Optional.of(company));

        assertThatThrownBy(() -> companyVerificationService.review(
                verificationId, "REJECT", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do từ chối");
        verify(companyRepository, never()).save(any());
    }
}
