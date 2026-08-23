package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Amount requested by the authenticated account for a SePay checkout order. */
public record TopUpRequest(
        @NotNull
        @DecimalMin(value = "10000", message = "Minimum top-up amount is 10000 VND")
        BigDecimal amount,
        
        String returnUrl
) {
}
