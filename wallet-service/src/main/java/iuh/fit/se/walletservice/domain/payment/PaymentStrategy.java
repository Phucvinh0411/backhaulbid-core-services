package iuh.fit.se.walletservice.domain.payment;

import java.math.BigDecimal;

@FunctionalInterface
public interface PaymentStrategy {
    PaymentResult pay(BigDecimal amount);
}
