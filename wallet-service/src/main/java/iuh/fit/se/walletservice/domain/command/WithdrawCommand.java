package iuh.fit.se.walletservice.domain.command;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;

import java.math.BigDecimal;

public record WithdrawCommand(Wallet wallet, BigDecimal amount) implements WalletCommand {
    @Override
    public Transaction execute() {
        return wallet.withdraw(amount);
    }
}
