package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.dto.RequestSignOtpResponse;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.ContractSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceOtpSigningTest {

    private static final Instant NOW = Instant.parse("2026-11-13T07:00:00Z");

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractSignatureRepository signatureRepository;
    @Mock
    private IdentityVerificationClient identityVerificationClient;
    @Mock
    private ContractOtpMailSender otpMailSender;
    @Mock
    private AwardEventPublisher awardEventPublisher;

    private ContractSigningOtpService otpService;
    private ContractService contractService;

    @BeforeEach
    void setUp() {
        otpService = new ContractSigningOtpService(600, 60, 5);
        contractService = new ContractService(
                contractRepository,
                signatureRepository,
                identityVerificationClient,
                otpService,
                otpMailSender,
                awardEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requestSignOtpIssuesHashedChallengeAndEmailsPlainOtp() {
        UUID contractId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .id(contractId)
                .contractCode("HD-2026-99")
                .shipperId(shipperId)
                .carrierId(carrierId)
                .status(ContractStatus.WAITING_SIGNATURE)
                .build();

        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(signatureRepository.findByContractIdAndAccountId(contractId, carrierId)).thenReturn(Optional.empty());
        when(identityVerificationClient.fetchSigningContact(carrierId))
                .thenReturn(new IdentityVerificationClient.SigningContact("carrier.boss@gmail.com", true, "Cong ty Van Tai"));

        RequestSignOtpResponse response = contractService.requestSignOtp(
                carrierId, AccountRole.CARRIER, contractId, "127.0.0.1");

        assertThat(response.maskedEmail()).isEqualTo("ca***@gmail.com");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(response.resendAfterSeconds()).isEqualTo(60L);

        ArgumentCaptor<ContractSignature> savedSignature = ArgumentCaptor.forClass(ContractSignature.class);
        verify(signatureRepository).save(savedSignature.capture());
        assertThat(savedSignature.getValue().getOtpCodeHashed()).isNotBlank();

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpMailSender).sendSigningOtp(
                eq("carrier.boss@gmail.com"),
                eq("Cong ty Van Tai"),
                eq("HD-2026-99"),
                otpCaptor.capture(),
                eq(10L));
        assertThat(otpCaptor.getValue()).matches("\\d{6}");
        assertThat(savedSignature.getValue().getOtpCodeHashed())
                .isEqualTo(otpService.hashOtp(contractId, carrierId, otpCaptor.getValue()));
    }

    @Test
    void requestSignOtpRejectsWhenAccountHasNoEmail() {
        UUID contractId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .id(contractId)
                .contractCode("HD-2026-100")
                .shipperId(shipperId)
                .carrierId(carrierId)
                .status(ContractStatus.WAITING_SIGNATURE)
                .build();

        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(signatureRepository.findByContractIdAndAccountId(contractId, shipperId)).thenReturn(Optional.empty());
        when(identityVerificationClient.fetchSigningContact(shipperId))
                .thenReturn(new IdentityVerificationClient.SigningContact(null, false, "Chu hang"));

        assertThatThrownBy(() -> contractService.requestSignOtp(
                shipperId, AccountRole.SHIPPER, contractId, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(otpMailSender);
    }

    @Test
    void signRejectsWrongOtpAndSavesAttemptCountThenSucceedsWithValidOtp() {
        UUID contractId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Trip trip = Trip.builder()
                .id(tripId)
                .awardAttemptId("attempt-otp-1")
                .build();
        Contract contract = Contract.builder()
                .id(contractId)
                .trip(trip)
                .contractCode("HD-2026-101")
                .shipperId(shipperId)
                .carrierId(carrierId)
                .status(ContractStatus.WAITING_SIGNATURE)
                .build();

        ContractSignature signature = ContractSignature.builder()
                .contract(contract)
                .accountId(carrierId)
                .role(AccountRole.CARRIER)
                .build();
        var issued = otpService.issueChallenge(signature, contractId, carrierId, NOW.minusSeconds(15));
        contract.addSignature(signature);

        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(signatureRepository.findByContractIdAndAccountId(contractId, carrierId)).thenReturn(Optional.of(signature));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        String wrongOtp = "000000".equals(issued.otp()) ? "000001" : "000000";
        assertThatThrownBy(() -> contractService.sign(
                carrierId, AccountRole.CARRIER, contractId, "10.0.0.1", wrongOtp))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(signature.getOtpAttempts()).isEqualTo(1);
        assertThat(signature.getSignedAt()).isNull();

        Contract updated = contractService.sign(
                carrierId, AccountRole.CARRIER, contractId, "10.0.0.1", issued.otp());
        assertThat(signature.getSignedAt()).isNotNull();
        assertThat(signature.getOtpCodeHashed()).isNotBlank();
        assertThat(updated.getStatus()).isEqualTo(ContractStatus.WAITING_SIGNATURE);
        verify(awardEventPublisher).publishAfterCommit(
                "attempt-otp-1",
                contractId,
                tripId,
                ContractStatus.WAITING_SIGNATURE);
    }
}
