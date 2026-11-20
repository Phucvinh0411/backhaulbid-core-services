package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Thiếu mã xác thực Google (idToken)")
        String idToken) {
}
