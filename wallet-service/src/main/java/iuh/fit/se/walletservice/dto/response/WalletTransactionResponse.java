package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id,
        BigDecimal amount,
        String type,
        String status,
        String paymentMethod,
        String referenceCode,
        String description,
        Instant createdAt
) {
}
