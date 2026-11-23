package iuh.fit.se.service;

import iuh.fit.se.domain.entity.EmailChangeOtp;
import iuh.fit.se.repository.EmailChangeOtpRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeOtpServiceTest {

    @Mock
    private EmailChangeOtpRepository otpRepository;
    @Mock
    private AccountMailSender mailSender;

    @Test
    void enforcesCooldownAndVerifiesValidOtp() {
        Instant now = Instant.parse("2026-11-24T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        EmailChangeOtpService service = new EmailChangeOtpService(otpRepository, mailSender, clock, 600, 60, 5);
        UUID accountId = UUID.randomUUID();
        String newEmail = "updated@company.vn";

        when(otpRepository.findById(accountId)).thenReturn(Optional.empty());
        var sent = service.sendOtp(accountId, newEmail, "Thu Ha");
        assertThat(sent.maskedEmail()).isEqualTo("up***@company.vn");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendEmailChangeOtp(eq(newEmail), eq("Thu Ha"), otpCaptor.capture(), eq(10L));

        ArgumentCaptor<EmailChangeOtp> savedCaptor = ArgumentCaptor.forClass(EmailChangeOtp.class);
        verify(otpRepository).save(savedCaptor.capture());
        EmailChangeOtp saved = savedCaptor.getValue();

        when(otpRepository.findById(accountId)).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.sendOtp(accountId, newEmail, "Thu Ha"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        assertThatThrownBy(() -> service.verifyAndConsumeOtp(accountId, newEmail, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(saved.getAttempts()).isEqualTo(1);

        service.verifyAndConsumeOtp(accountId, newEmail, otpCaptor.getValue());
        verify(otpRepository).delete(saved);
    }
}
