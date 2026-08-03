package iuh.fit.se.service;

import iuh.fit.se.domain.entity.Account;
import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.EkycSubmitRequest;
import iuh.fit.se.repository.AccountRepository;
import iuh.fit.se.repository.EkycVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EkycServiceTest {

    private static final String ACCOUNT_ID = "8f14e45f-ea43-4a4f-b716-3f8f68f74201";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EkycVerificationRepository ekycVerificationRepository;

    @InjectMocks
    private EkycService ekycService;

    @Test
    void processEkyc_authenticatedAccount_savesVerificationForAccount() {
        // Given
        Account account = Account.builder()
                .id(UUID.fromString(ACCOUNT_ID))
                .phone("0901234567")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();
        EkycSubmitRequest request = validRequest(true);
        EkycVerification savedVerification = EkycVerification.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .account(account)
                .status(VerificationStatus.VERIFIED)
                .build();
        ArgumentCaptor<EkycVerification> verificationCaptor =
                ArgumentCaptor.forClass(EkycVerification.class);
        when(accountRepository.findById(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(ekycVerificationRepository.save(verificationCaptor.capture()))
                .thenReturn(savedVerification);

        // When
        EkycVerification actualVerification = ekycService.processEkyc(ACCOUNT_ID, request);

        // Then
        assertThat(actualVerification.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(verificationCaptor.getValue().getAccount()).isSameAs(account);
        assertThat(verificationCaptor.getValue().getIdentityNumber()).isEqualTo("079123456789");
        assertThat(verificationCaptor.getValue().getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(verificationCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void processEkyc_failedLiveness_setsRejectedStatus() {
        // Given
        Account account = Account.builder()
                .id(UUID.fromString(ACCOUNT_ID))
                .phone("0901234567")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();
        EkycSubmitRequest request = validRequest(false);
        EkycVerification savedVerification = EkycVerification.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .account(account)
                .status(VerificationStatus.REJECTED)
                .build();
        ArgumentCaptor<EkycVerification> verificationCaptor =
                ArgumentCaptor.forClass(EkycVerification.class);
        when(accountRepository.findById(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(ekycVerificationRepository.save(verificationCaptor.capture()))
                .thenReturn(savedVerification);

        // When
        ekycService.processEkyc(ACCOUNT_ID, request);

        // Then
        assertThat(verificationCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.REJECTED);
    }

    @Test
    void processEkyc_failedDocumentAuthenticity_rejectsAndStoresReason() {
        Account account = Account.builder()
                .id(UUID.fromString(ACCOUNT_ID))
                .phone("0901234567")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();
        EkycSubmitRequest request = validRequest(true);
        request.setDocumentAuthenticityPassed(false);
        ArgumentCaptor<EkycVerification> verificationCaptor =
                ArgumentCaptor.forClass(EkycVerification.class);
        when(accountRepository.findById(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(ekycVerificationRepository.save(verificationCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EkycVerification result = ekycService.processEkyc(ACCOUNT_ID, request);

        assertThat(result.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.getFailureReason()).contains("chụp lại");
        assertThat(verificationCaptor.getValue().getDocumentAuthenticityPassed()).isFalse();
    }

    @Test
    void processEkyc_failedOcr_rejectsAndStoresReason() {
        Account account = Account.builder()
                .id(UUID.fromString(ACCOUNT_ID))
                .phone("0901234567")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();
        EkycSubmitRequest request = validRequest(true);
        request.setOcrPassed(false);
        when(accountRepository.findById(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(ekycVerificationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EkycVerification result = ekycService.processEkyc(ACCOUNT_ID, request);

        assertThat(result.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.getFailureReason()).contains("CCCD");
    }

    @Test
    void processEkyc_successChecksWithoutIdentity_rejectAndExplainMissingOcrData() {
        Account account = Account.builder()
                .id(UUID.fromString(ACCOUNT_ID))
                .phone("0901234567")
                .role(AccountRole.CARRIER)
                .status(AccountStatus.ACTIVE)
                .build();
        EkycSubmitRequest request = validRequest(true);
        request.setIdentityNumber(null);
        request.setFullName(null);
        when(accountRepository.findById(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(Optional.of(account));
        when(ekycVerificationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EkycVerification result = ekycService.processEkyc(ACCOUNT_ID, request);

        assertThat(result.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.getFailureReason()).contains("OCR");
    }

    @Test
    void processEkyc_unknownAccount_throwsException() {
        // Given
        EkycSubmitRequest request = validRequest(true);
        when(accountRepository.findById(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(Optional.empty());

        // When-Then
        assertThatThrownBy(() -> ekycService.processEkyc(ACCOUNT_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account not found");
        verify(ekycVerificationRepository, never()).save(
                org.mockito.ArgumentMatchers.any(EkycVerification.class));
    }

    private EkycSubmitRequest validRequest(boolean livenessPassed) {
        EkycSubmitRequest request = new EkycSubmitRequest();
        request.setIdentityNumber("079123456789");
        request.setFullName("Nguyễn Văn A");
        request.setFrontImageUrl("front.png");
        request.setBackImageUrl("back.png");
        request.setSelfieImageUrl("selfie.png");
        request.setOcrPassed(true);
        request.setDocumentLivenessPassed(true);
        request.setDocumentAuthenticityPassed(true);
        request.setLivenessPassed(livenessPassed);
        request.setFaceMatched(true);
        request.setFaceMatchScore(98.5);
        return request;
    }
}
