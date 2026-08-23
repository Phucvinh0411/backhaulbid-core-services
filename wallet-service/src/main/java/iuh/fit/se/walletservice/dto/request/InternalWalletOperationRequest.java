package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Validated input for wallet holds and charges.
 *
 * The idempotency key is part of the API contract because retrying a payment
 * request must not create a second money movement.
 */
public record InternalWalletOperationRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String auctionId,
        String registrationId,
        @NotBlank String purpose,
        @NotBlank String idempotencyKey
) {
}
