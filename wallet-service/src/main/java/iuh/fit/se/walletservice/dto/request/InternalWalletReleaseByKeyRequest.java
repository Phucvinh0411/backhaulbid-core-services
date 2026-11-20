package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Input used by Saga crash recovery to release an orphaned hold using its original hold key. */
public record InternalWalletReleaseByKeyRequest(
        @NotBlank String holdIdempotencyKey,
        @NotBlank String idempotencyKey
) {
}
