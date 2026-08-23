package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.BusinessLookupResponse;
import iuh.fit.se.dto.BusinessVerificationResponse;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.CompanyRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyVerificationServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private BusinessVerificationService businessVerificationService;
    @Mock
    private BusinessVerificationMapper businessVerificationMapper;
    @Mock
    private EkycVerificationRepository ekycVerificationRepository;

    @InjectMocks
    private CompanyVerificationService companyVerificationService;

    private UUID accountId;
    private Account account;
    private Company company;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = Account.builder().id(accountId).build();
        company = Company.builder()
                .id(UUID.randomUUID())
                .account(account)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
    }

    /**
     * Feature: Carrier manages business profile
     * Scenario: Submit profile successfully
     */
    @Test
    void givenValidData_whenSubmit_thenSaveAndReturnPending() {
        String taxCode = "0123456789";
        EkycVerification ekyc = EkycVerification.builder().fullName("Nguyen Van A").status(VerificationStatus.VERIFIED).build();
        BusinessLookupResponse lookup = BusinessLookupResponse.builder()
                .valid(true).companyName("Cong Ty A").address("Ha Noi").representative("Nguyen Van A")
                .build();
        
        BusinessVerificationResponse mockedResponse = BusinessVerificationResponse.builder()
                .status(VerificationStatus.PENDING.name())
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(companyRepository.existsByTaxCodeAndAccount_IdNot(taxCode, accountId)).thenReturn(false);
        when(businessVerificationService.lookupByTaxCode(taxCode)).thenReturn(lookup);
        when(ekycVerificationRepository.findByAccount_Id(accountId)).thenReturn(Optional.of(ekyc));
        when(companyRepository.findByAccount_Id(accountId)).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(businessVerificationMapper.toResponse(any(Company.class))).thenReturn(mockedResponse);

        BusinessVerificationResponse response = companyVerificationService.submit(
                accountId.toString(), taxCode, "Nguyen Van A", "url1", "url2"
        );

        assertNotNull(response);
        assertEquals(VerificationStatus.PENDING.name(), response.getStatus());
        verify(companyRepository).save(any(Company.class));
    }

    /**
     * Feature: Admin reviews business profile
     * Scenario: Approve profile
     */
    @Test
    void givenPendingProfile_whenReviewApprove_thenSetVerified() {
        BusinessVerificationResponse mockedResponse = BusinessVerificationResponse.builder()
                .status(VerificationStatus.VERIFIED.name())
                .build();
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(businessVerificationMapper.toResponse(any(Company.class))).thenReturn(mockedResponse);

        companyVerificationService.review(company.getId(), "APPROVE", null);

        assertEquals(VerificationStatus.VERIFIED, company.getVerificationStatus());
        assertNull(company.getRejectionReason());
        verify(companyRepository).save(company);
    }

    /**
     * Feature: Admin reviews business profile
     * Scenario: Reject profile with reason
     */
    @Test
    void givenPendingProfile_whenReviewReject_thenSetRejectedAndReason() {
        BusinessVerificationResponse mockedResponse = BusinessVerificationResponse.builder()
                .status(VerificationStatus.REJECTED.name())
                .rejectionReason("Invalid paper")
                .build();
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(businessVerificationMapper.toResponse(any(Company.class))).thenReturn(mockedResponse);

        companyVerificationService.review(company.getId(), "REJECT", "Invalid paper");

        assertEquals(VerificationStatus.REJECTED, company.getVerificationStatus());
        assertEquals("Invalid paper", company.getRejectionReason());
        verify(companyRepository).save(company);
    }
}
