package iuh.fit.se.domain.dto.request;

import iuh.fit.se.domain.enums.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CompleteGoogleRegistrationRequest(
        @NotBlank(message = "Thiếu phiên đăng ký Google")
        String onboardingToken,

        @NotNull(message = "Vui lòng chọn vai trò tài khoản")
        AccountRole role,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "[0-9]{9,11}", message = "Số điện thoại phải gồm 9 đến 11 chữ số")
        String phone) {
}
