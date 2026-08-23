package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** Read-only polling payload so the portal can track a SePay top-up without waiting for the redirect. */
public record TopUpStatusResponse(
        String invoiceNumber,
        String status,
        BigDecimal amount,
        Instant paidAt,
        Instant expiresAt
) {
}
