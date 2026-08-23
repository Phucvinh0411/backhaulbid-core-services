package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Input used to make release and forfeit operations safely retryable. */
public record InternalWalletReleaseRequest(@NotBlank String idempotencyKey) {
}
