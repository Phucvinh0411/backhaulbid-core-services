package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WithdrawalRequestDto(
        @NotNull
        @DecimalMin(value = "10000", message = "Minimum withdrawal amount is 10000 VND")
        BigDecimal amount,
        @NotBlank @Size(max = 100)
        String bankName,
        @NotBlank @Pattern(regexp = "[0-9]{6,30}", message = "Bank account number must contain 6-30 digits")
        String bankAccountNumber,
        @NotBlank @Size(max = 120)
        String accountHolderName
) {
}
