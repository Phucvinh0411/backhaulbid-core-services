package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TopUpResponse(
        UUID paymentOrderId,
        String invoiceNumber,
        BigDecimal amount,
        String status,
        String checkoutUrl,
        Map<String, String> formFields,
        Instant expiresAt
) {
}
