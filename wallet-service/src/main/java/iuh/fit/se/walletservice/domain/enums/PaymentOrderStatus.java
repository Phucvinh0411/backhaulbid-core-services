package iuh.fit.se.walletservice.domain.enums;

/** Lifecycle states for a payment order created before the provider callback. */
public enum PaymentOrderStatus {
    CREATED,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED
}
