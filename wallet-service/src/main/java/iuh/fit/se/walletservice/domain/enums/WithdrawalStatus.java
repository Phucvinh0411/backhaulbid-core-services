package iuh.fit.se.walletservice.domain.enums;

/** State machine for a manual withdrawal payout request. */
public enum WithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
