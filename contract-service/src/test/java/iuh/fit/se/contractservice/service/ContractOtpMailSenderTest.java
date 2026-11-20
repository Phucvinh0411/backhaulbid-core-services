package iuh.fit.se.contractservice.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractOtpMailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsMimeEmailWithBrevoCompatibleConfiguration() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ContractOtpMailSender sender = new ContractOtpMailSender(
                mailSender, true, "smtp-relay.brevo.com", "noreply@backhaulbid.vn", "BackhaulBid");

        sender.sendSigningOtp("partner@company.vn", "Cong ty Van Tai <A>", "HD-2026-001", "654321", 10);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).contains("HD-2026-001");
    }

    @Test
    void failsClosedWhenUnconfiguredOrDeliveryFails() {
        ContractOtpMailSender unconfigured = new ContractOtpMailSender(
                mailSender, true, "", "noreply@backhaulbid.vn", "BackhaulBid");

        assertThatThrownBy(() -> unconfigured.sendSigningOtp("a@b.com", "A", "HD-1", "111111", 10))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP rejected")).when(mailSender).send(any(MimeMessage.class));

        ContractOtpMailSender broken = new ContractOtpMailSender(
                mailSender, true, "smtp-relay.brevo.com", "noreply@backhaulbid.vn", "BackhaulBid");

        assertThatThrownBy(() -> broken.sendSigningOtp("a@b.com", "A", "HD-1", "111111", 10))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
