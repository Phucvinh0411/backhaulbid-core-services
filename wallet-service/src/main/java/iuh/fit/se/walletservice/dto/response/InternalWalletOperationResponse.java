package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Stable response contract returned to internal money-movement consumers. */
public record InternalWalletOperationResponse(
        String status,
        UUID transactionId,
        UUID holdId,
        BigDecimal amount,
        String message
) {
}
