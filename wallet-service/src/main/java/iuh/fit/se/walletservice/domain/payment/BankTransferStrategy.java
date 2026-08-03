package iuh.fit.se.walletservice.domain.payment;

import java.math.BigDecimal;
import java.util.function.Function;

public final class BankTransferStrategy implements PaymentStrategy {
    private final Function<BigDecimal, PaymentResult> paymentGateway;

    public BankTransferStrategy(Function<BigDecimal, PaymentResult> paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Override
    public PaymentResult pay(BigDecimal amount) {
        return paymentGateway.apply(amount);
    }
}
