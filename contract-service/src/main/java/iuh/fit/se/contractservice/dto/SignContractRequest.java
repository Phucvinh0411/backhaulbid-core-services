package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignContractRequest(
        @NotBlank(message = "Vui lòng nhập mã OTP")
        @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm đúng 6 chữ số")
        String otpCode) {
}
