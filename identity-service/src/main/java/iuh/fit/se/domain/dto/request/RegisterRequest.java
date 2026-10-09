package iuh.fit.se.domain.dto.request;

import iuh.fit.se.domain.enums.AccountRole;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

@Data
public class RegisterRequest {
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "[0-9]{9,11}", message = "Phone must contain 9 to 11 digits")
    private String phone;
    @Email(message = "Email is invalid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must have at least 8 characters and at most 72 bytes")
    @lombok.ToString.Exclude
    private String password;
    @NotNull(message = "Role is required")
    private AccountRole role;

    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPasswordByteLengthValid() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
