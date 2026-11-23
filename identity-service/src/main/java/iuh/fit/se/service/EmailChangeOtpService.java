package iuh.fit.se.service;

import iuh.fit.se.domain.dto.response.SendEmailOtpResponse;
import iuh.fit.se.domain.entity.EmailChangeOtp;
import iuh.fit.se.repository.EmailChangeOtpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class EmailChangeOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailChangeOtpRepository otpRepository;
    private final AccountMailSender mailSender;
    private final Clock clock;
    private final long otpTtlSeconds;
    private final long resendCooldownSeconds;
    private final int maxAttempts;

    @Autowired
    public EmailChangeOtpService(
            EmailChangeOtpRepository otpRepository,
            AccountMailSender mailSender,
            @Value("${app.mail.otp-ttl-seconds:600}") long otpTtlSeconds,
            @Value("${app.mail.otp-resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.mail.otp-max-attempts:5}") int maxAttempts) {
        this(otpRepository, mailSender, Clock.systemUTC(), otpTtlSeconds, resendCooldownSeconds, maxAttempts);
    }

    EmailChangeOtpService(
            EmailChangeOtpRepository otpRepository,
            AccountMailSender mailSender,
            Clock clock,
            long otpTtlSeconds,
            long resendCooldownSeconds,
            int maxAttempts) {
        this.otpRepository = otpRepository;
        this.mailSender = mailSender;
        this.clock = clock;
        this.otpTtlSeconds = Math.max(60L, otpTtlSeconds);
        this.resendCooldownSeconds = Math.max(10L, resendCooldownSeconds);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public SendEmailOtpResponse sendOtp(UUID accountId, String normalizedEmail, String recipientName) {
        Instant now = clock.instant();
        EmailChangeOtp existing = otpRepository.findById(accountId).orElse(null);
        if (existing != null
                && existing.getSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)
                && existing.getPendingEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn vừa yêu cầu mã OTP. Vui lòng chờ trước khi gửi lại.");
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        long expiresMinutes = Math.max(1L, otpTtlSeconds / 60L);
        mailSender.sendEmailChangeOtp(normalizedEmail, recipientName, otp, expiresMinutes);

        EmailChangeOtp challenge = EmailChangeOtp.builder()
                .accountId(accountId)
                .pendingEmail(normalizedEmail)
                .otpHash(hashOtp(accountId, normalizedEmail, otp))
                .expiresAt(now.plusSeconds(otpTtlSeconds))
                .sentAt(now)
                .attempts(0)
                .build();
        otpRepository.save(challenge);
        return new SendEmailOtpResponse(maskEmail(normalizedEmail), resendCooldownSeconds, expiresMinutes);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verifyAndConsumeOtp(UUID accountId, String normalizedEmail, String rawOtp) {
        EmailChangeOtp challenge = otpRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Bạn chưa gửi mã OTP cho email mới. Vui lòng nhấn Gửi mã OTP trước khi xác nhận."));

        if (!challenge.getPendingEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email xác nhận không khớp với email đã nhận mã OTP. Vui lòng gửi lại mã OTP mới.");
        }
        if (challenge.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng gửi lại mã OTP mới.");
        }
        Instant now = clock.instant();
        if (challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP đã hết hạn. Vui lòng gửi lại mã OTP mới.");
        }

        String candidate = hashOtp(accountId, normalizedEmail, rawOtp == null ? "" : rawOtp.trim());
        byte[] expectedBytes = challenge.getOtpHash().getBytes(StandardCharsets.UTF_8);
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, candidateBytes)) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            otpRepository.save(challenge);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP không chính xác. Vui lòng kiểm tra lại.");
        }
        otpRepository.delete(challenge);
    }

    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }

    String hashOtp(UUID accountId, String normalizedEmail, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = "email-change-otp:" + accountId + ":" + normalizedEmail + ":" + otp;
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
