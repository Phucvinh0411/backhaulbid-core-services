package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID accountId,
        BigDecimal balance,
        BigDecimal frozenBalance,
        BigDecimal availableBalance,
        Instant updatedAt
) {
}
