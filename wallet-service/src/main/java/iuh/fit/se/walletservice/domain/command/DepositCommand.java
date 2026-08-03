package iuh.fit.se.walletservice.domain.command;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.domain.payment.PaymentResult;
import iuh.fit.se.walletservice.domain.payment.PaymentStrategy;

import java.math.BigDecimal;

public final class DepositCommand implements WalletCommand {
    private final Wallet wallet;
    private final BigDecimal amount;
    private final PaymentStrategy paymentStrategy;

    public DepositCommand(Wallet wallet, BigDecimal amount, PaymentStrategy paymentStrategy) {
        this.wallet = wallet;
        this.amount = amount;
        this.paymentStrategy = paymentStrategy;
    }

    @Override
    public Transaction execute() {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        PaymentResult result = paymentStrategy.pay(amount);
        if (!result.success()) {
            return wallet.recordFailedTransaction(amount, TransactionType.DEPOSIT, result.message());
        }
        Transaction transaction = wallet.deposit(amount);
        transaction.setReferenceCode(result.transactionCode());
        return transaction;
    }
}
