package iuh.fit.se.walletservice.domain.command;

import iuh.fit.se.walletservice.domain.entity.Transaction;

public interface WalletCommand {
    Transaction execute();
}
