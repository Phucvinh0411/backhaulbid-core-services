package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendEmailOtpRequest(
        @NotBlank(message = "Email không được để trống")
        @Size(max = 254, message = "Email tối đa 254 ký tự")
        @Pattern(
                regexp = "^[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,10}$",
                message = "Email không đúng định dạng (ví dụ: ten@tenmien.vn)"
        )
        String email) {
}
