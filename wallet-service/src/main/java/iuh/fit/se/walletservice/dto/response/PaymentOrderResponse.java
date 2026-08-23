package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public payment-order metadata used by history and admin audit screens. */
public record PaymentOrderResponse(
        UUID paymentOrderId,
        UUID accountId,
        String invoiceNumber,
        BigDecimal amount,
        String provider,
        String status,
        String checkoutUrl,
        String providerTransactionId,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {
}
