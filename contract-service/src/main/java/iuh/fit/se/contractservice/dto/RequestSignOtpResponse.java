package iuh.fit.se.contractservice.dto;

import java.time.Instant;

public record RequestSignOtpResponse(
        String maskedEmail,
        Instant expiresAt,
        long resendAfterSeconds) {
}
