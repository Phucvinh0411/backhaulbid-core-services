package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record UpdatePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword,

        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Size(min = 8, max = 72, message = "Mật khẩu mới cần tối thiểu 8 ký tự và tối đa 72 ký tự")
        String newPassword) {

    @AssertTrue(message = "Mật khẩu mới không được vượt quá 72 byte UTF-8")
    public boolean isNewPasswordByteLengthValid() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
