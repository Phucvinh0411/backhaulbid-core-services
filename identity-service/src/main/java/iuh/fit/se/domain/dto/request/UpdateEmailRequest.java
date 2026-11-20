package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEmailRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 254, message = "Email tối đa 254 ký tự")
        String email) {
}
