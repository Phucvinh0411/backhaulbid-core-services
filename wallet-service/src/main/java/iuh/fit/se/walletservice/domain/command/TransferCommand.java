package iuh.fit.se.walletservice.domain.command;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;

import java.math.BigDecimal;

public record TransferCommand(Wallet sender, Wallet receiver, BigDecimal amount)
        implements WalletCommand {
    @Override
    public Transaction execute() {
        return sender.transferTo(receiver, amount);
    }
}
