package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalResponse(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String bankName,
        String maskedBankAccountNumber,
        String accountHolderName,
        String status,
        UUID processedBy,
        String rejectionReason,
        Instant createdAt,
        Instant processedAt
) {
}
