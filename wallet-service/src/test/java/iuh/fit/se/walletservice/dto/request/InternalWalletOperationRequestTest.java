package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InternalWalletOperationRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testRegistrationIdIsOptional() {
        InternalWalletOperationRequest request = new InternalWalletOperationRequest(
                new BigDecimal("100"),
                "auction-123",
                null,
                "purpose",
                "idemp-123"
        );

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
