package iuh.fit.se.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** Sends UTF-8 OTP emails for email address verification over the configured SMTP relay. */
@Slf4j
@Component
public class AccountMailSender {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String mailHost;
    private final String fromAddress;
    private final String fromName;

    @Autowired
    public AccountMailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.enabled:true}") boolean enabled,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from:no-reply@backhaulbid.vn}") String fromAddress,
            @Value("${app.mail.from-name:BackhaulBid}") String fromName) {
        this(mailSenderProvider.getIfAvailable(), enabled, mailHost, fromAddress, fromName);
    }

    AccountMailSender(
            JavaMailSender mailSender,
            boolean enabled,
            String mailHost,
            String fromAddress,
            String fromName) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        this.fromName = fromName == null || fromName.isBlank() ? "BackhaulBid" : fromName.trim();
    }

    public void sendEmailChangeOtp(String toEmail, String recipientName, String otpCode, long expiresMinutes) {
        if (!enabled || mailSender == null || mailHost.isBlank() || fromAddress.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ gửi email OTP chưa được cấu hình. Vui lòng thử lại sau.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(toEmail);
            helper.setSubject("[BackhaulBid] Mã OTP xác nhận thay đổi email");

            String greeting = recipientName != null && !recipientName.isBlank() ? recipientName : "Quý khách";
            String plainText = """
                    Xin chào %s,

                    Mã OTP xác nhận thay đổi email tài khoản BackhaulBid của bạn là: %s
                    Mã có hiệu lực trong %d phút. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.

                    Trân trọng,
                    BackhaulBid""".formatted(greeting, otpCode, expiresMinutes);

            String htmlBody = """
                    <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#1f2937;">
                      <h2 style="color:#0f4c81;">Xác nhận thay đổi email</h2>
                      <p>Xin chào <strong>%s</strong>,</p>
                      <p>Bạn vừa yêu cầu cập nhật địa chỉ email trên hệ thống BackhaulBid.</p>
                      <div style="margin:20px 0;padding:16px;background:#f3f4f6;border-radius:8px;text-align:center;">
                        <div style="font-size:12px;color:#6b7280;margin-bottom:6px;">MÃ XÁC NHẬN OTP</div>
                        <div style="font-size:28px;font-weight:700;letter-spacing:6px;color:#0f4c81;">%s</div>
                      </div>
                      <p>Mã OTP có hiệu lực trong <strong>%d phút</strong>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>
                    </div>""".formatted(
                    HtmlUtils.htmlEscape(greeting),
                    HtmlUtils.htmlEscape(otpCode),
                    expiresMinutes);

            helper.setText(plainText, htmlBody);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn("Failed to deliver email-change OTP email to {}", mask(toEmail), ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể gửi email OTP lúc này. Vui lòng kiểm tra địa chỉ email và thử lại sau.");
        }
    }

    private static String mask(String email) {
        if (email == null) return "***";
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.substring(0, 2) + "***" + email.substring(at);
    }
}
