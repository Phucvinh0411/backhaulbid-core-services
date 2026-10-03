package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalWalletSettlementRequest(
        @NotNull UUID recipientAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String idempotencyKey,
        @NotBlank String tripId,
        @NotNull @Min(1) @Max(3) Integer tier
) {}
