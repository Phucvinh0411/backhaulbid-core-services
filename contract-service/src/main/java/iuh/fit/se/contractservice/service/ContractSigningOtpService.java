package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ContractSigningOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final long otpTtlSeconds;
    private final long resendCooldownSeconds;
    private final int maxAttempts;

    @Autowired
    public ContractSigningOtpService(
            @Value("${app.mail.otp-ttl-seconds:600}") long otpTtlSeconds,
            @Value("${app.mail.otp-resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.mail.otp-max-attempts:5}") int maxAttempts) {
        this.otpTtlSeconds = Math.max(60L, otpTtlSeconds);
        this.resendCooldownSeconds = Math.max(10L, resendCooldownSeconds);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public record IssuedOtp(String otp, Instant expiresAt, long resendAfterSeconds, long expiresMinutes) {
    }

    public void assertCanRequestOtp(ContractSignature signature, Instant now) {
        if (signature.getSignedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hợp đồng đã được bạn ký trước đó.");
        }
        if (signature.getOtpSentAt() != null
                && signature.getOtpSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn vừa yêu cầu mã OTP. Vui lòng chờ trước khi gửi lại.");
        }
    }

    public IssuedOtp issueChallenge(ContractSignature signature, UUID contractId, UUID accountId, Instant now) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = now.plusSeconds(otpTtlSeconds);
        signature.setOtpCodeHashed(hashOtp(contractId, accountId, otp));
        signature.setOtpExpiresAt(expiresAt);
        signature.setOtpSentAt(now);
        signature.setOtpAttempts(0);
        return new IssuedOtp(otp, expiresAt, resendCooldownSeconds, Math.max(1L, otpTtlSeconds / 60L));
    }

    public boolean verifyChallenge(ContractSignature signature, UUID contractId, UUID accountId, String rawOtp, Instant now) {
        if (signature == null || signature.getOtpCodeHashed() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bạn chưa gửi mã OTP. Vui lòng nhấn Gửi mã OTP về email trước khi ký.");
        }
        if (signature.getSignedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hợp đồng đã được bạn ký trước đó.");
        }
        if (signature.getOtpAttempts() >= maxAttempts) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng gửi lại mã OTP mới.");
        }
        if (signature.getOtpExpiresAt() == null || !signature.getOtpExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP đã hết hạn. Vui lòng gửi lại mã OTP mới.");
        }

        String candidate = hashOtp(contractId, accountId, rawOtp == null ? "" : rawOtp.trim());
        byte[] expectedBytes = signature.getOtpCodeHashed().getBytes(StandardCharsets.UTF_8);
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, candidateBytes)) {
            signature.setOtpAttempts(signature.getOtpAttempts() + 1);
            return false;
        }
        signature.setOtpExpiresAt(null);
        return true;
    }

    public String maskEmail(String email) {
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

    String hashOtp(UUID contractId, UUID accountId, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = "contract-sign-otp:" + contractId + ":" + accountId + ":" + otp;
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
