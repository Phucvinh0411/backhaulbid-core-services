package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractSigningOtpServiceTest {

    private final ContractSigningOtpService service = new ContractSigningOtpService(600, 60, 5);

    @Test
    void issueAndVerifyChallengeRoundTrip() {
        UUID contractId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-13T07:00:00Z");
        ContractSignature signature = ContractSignature.builder()
                .contract(null)
                .accountId(accountId)
                .build();

        var issued = service.issueChallenge(signature, contractId, accountId, now);

        assertThat(issued.otp()).matches("\\d{6}");
        assertThat(signature.getOtpCodeHashed()).isNotBlank().isNotEqualTo(issued.otp());
        assertThat(signature.getOtpExpiresAt()).isEqualTo(now.plusSeconds(600));
        assertThat(signature.getOtpSentAt()).isEqualTo(now);
        assertThat(signature.getOtpAttempts()).isZero();

        assertThat(service.verifyChallenge(signature, contractId, accountId, "000000".equals(issued.otp()) ? "000001" : "000000", now.plusSeconds(10))).isFalse();
        assertThat(signature.getOtpAttempts()).isEqualTo(1);

        assertThat(service.verifyChallenge(signature, contractId, accountId, issued.otp(), now.plusSeconds(20))).isTrue();
        assertThat(signature.getOtpExpiresAt()).isNull();
    }

    @Test
    void enforcesCooldownExpiryAndMaxAttempts() {
        UUID contractId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-13T07:00:00Z");
        ContractSignature signature = ContractSignature.builder().accountId(accountId).build();

        service.issueChallenge(signature, contractId, accountId, now);

        assertThatThrownBy(() -> service.assertCanRequestOtp(signature, now.plusSeconds(30)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThatThrownBy(() -> service.verifyChallenge(signature, contractId, accountId, "123456", now.plusSeconds(601)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        signature.setOtpAttempts(5);
        assertThatThrownBy(() -> service.verifyChallenge(signature, contractId, accountId, "123456", now.plusSeconds(10)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void masksEmailSafely() {
        assertThat(service.maskEmail("a@gmail.com")).isEqualTo("a***@gmail.com");
        assertThat(service.maskEmail("nguyen.van.a@company.vn")).isEqualTo("ng***@company.vn");
    }
}
