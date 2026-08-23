package iuh.fit.se.walletservice.domain.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentOrderUniquenessTest {
    @Test
    void providerTransactionIdIsUniqueWhenPresent() throws NoSuchFieldException {
        Field field = PaymentOrder.class.getDeclaredField("providerTransactionId");

        assertTrue(field.getAnnotation(Column.class).unique(),
                "provider transaction IDs must be unique to protect IPN idempotency");
    }
}
