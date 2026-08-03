package iuh.fit.se.walletservice.domain.entity;

import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    void deposit_positiveAmount_increasesBalanceAndCreatesSuccessfulTransaction() {
        // Given
        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("100.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();

        // When
        Transaction actualTransaction = wallet.deposit(new BigDecimal("50.00"));

        // Then
        assertThat(wallet.getBalance()).isEqualByComparingTo("150.00");
        assertThat(actualTransaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(actualTransaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void freeze_amountWithinAvailableBalance_increasesFrozenBalance() {
        // Given
        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("100.00"))
                .frozenBalance(new BigDecimal("20.00"))
                .build();

        // When
        wallet.freeze(new BigDecimal("30.00"));

        // Then
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
        assertThat(wallet.getFrozenBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void freeze_amountAboveAvailableBalance_throwsIllegalStateException() {
        // Given
        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("100.00"))
                .frozenBalance(new BigDecimal("80.00"))
                .build();

        // When / Then
        assertThatThrownBy(() -> wallet.freeze(new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient available balance");
    }

    @Test
    void unfreeze_amountAboveFrozenBalance_throwsIllegalStateException() {
        // Given
        Wallet wallet = Wallet.builder()
                .balance(new BigDecimal("100.00"))
                .frozenBalance(new BigDecimal("20.00"))
                .build();

        // When / Then
        assertThatThrownBy(() -> wallet.unfreeze(new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient frozen balance");
    }

    @Test
    void transferTo_sufficientAvailableBalance_movesAmountBetweenWallets() {
        // Given
        Wallet sender = Wallet.builder()
                .balance(new BigDecimal("100.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        Wallet receiver = Wallet.builder()
                .balance(new BigDecimal("25.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();

        // When
        Transaction actualTransaction = sender.transferTo(receiver, new BigDecimal("40.00"));

        // Then
        assertThat(sender.getBalance()).isEqualByComparingTo("60.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("65.00");
        assertThat(actualTransaction.getType()).isEqualTo(TransactionType.TRANSFER);
    }
}
