package iuh.fit.se.walletservice.domain.payment;

public record PaymentResult(boolean success, String transactionCode, String message) {
}
