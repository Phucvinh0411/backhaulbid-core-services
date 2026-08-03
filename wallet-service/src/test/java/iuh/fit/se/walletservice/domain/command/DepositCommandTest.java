package iuh.fit.se.walletservice.domain.command;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.payment.PaymentResult;
import iuh.fit.se.walletservice.domain.payment.PaymentStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositCommandTest {

    @Test
    void execute_failedExternalPayment_keepsBalanceAndRecordsFailure() {
        // Given
        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("100.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        PaymentStrategy failedPayment = amount ->
                new PaymentResult(false, null, "Payment declined");
        DepositCommand command = new DepositCommand(
                wallet, new BigDecimal("50.00"), failedPayment);

        // When
        Transaction actualTransaction = command.execute();

        // Then
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
        assertThat(actualTransaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(actualTransaction.getDescription()).isEqualTo("Payment declined");
    }

    @Test
    void execute_nonPositiveAmount_rejectsBeforeCallingPaymentGateway() {
        // Given
        Wallet wallet = Wallet.builder().build();
        PaymentStrategy paymentGatewayMustNotBeCalled = amount -> {
            throw new AssertionError("Payment gateway must not be called");
        };
        DepositCommand command = new DepositCommand(
                wallet, new BigDecimal("-1.00"), paymentGatewayMustNotBeCalled);

        // When / Then
        assertThatThrownBy(command::execute)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }
}
