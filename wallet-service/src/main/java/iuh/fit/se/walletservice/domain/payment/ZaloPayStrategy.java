package iuh.fit.se.walletservice.domain.payment;

import java.math.BigDecimal;
import java.util.function.Function;

public final class ZaloPayStrategy implements PaymentStrategy {
    private final Function<BigDecimal, PaymentResult> paymentGateway;

    public ZaloPayStrategy(Function<BigDecimal, PaymentResult> paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Override
    public PaymentResult pay(BigDecimal amount) {
        return paymentGateway.apply(amount);
    }
}
