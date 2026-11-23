package iuh.fit.se.domain.dto.response;

public record SendEmailOtpResponse(
        String maskedEmail,
        long resendAfterSeconds,
        long expiresMinutes) {
}
