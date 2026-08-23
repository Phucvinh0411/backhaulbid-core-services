package iuh.fit.se.walletservice.domain.operation;

import iuh.fit.se.walletservice.domain.entity.Transaction;

import java.util.UUID;

public record InternalWalletOperation(
        Transaction transaction,
        UUID holdId
) {
}
