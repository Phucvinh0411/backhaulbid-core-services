package iuh.fit.se.walletservice.domain.entity;

import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "frozen_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private long version;

    @Builder.Default
    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Transaction deposit(BigDecimal amount) {
        requirePositive(amount);
        balance = currentBalance().add(amount);
        return record(amount, TransactionType.DEPOSIT);
    }

    public Transaction withdraw(BigDecimal amount) {
        requireAvailable(amount);
        balance = currentBalance().subtract(amount);
        return record(amount, TransactionType.WITHDRAW);
    }

    public Transaction freeze(BigDecimal amount) {
        requireAvailable(amount);
        frozenBalance = currentFrozenBalance().add(amount);
        return record(amount, TransactionType.FREEZE);
    }

    public Transaction unfreeze(BigDecimal amount) {
        requirePositive(amount);
        if (currentFrozenBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient frozen balance");
        }
        frozenBalance = currentFrozenBalance().subtract(amount);
        return record(amount, TransactionType.UNFREEZE);
    }

    public Transaction transferTo(Wallet receiver, BigDecimal amount) {
        if (receiver == null || receiver == this) {
            throw new IllegalArgumentException("Receiver must be a different wallet");
        }
        requireAvailable(amount);
        balance = currentBalance().subtract(amount);
        receiver.balance = receiver.currentBalance().add(amount);
        return record(amount, TransactionType.TRANSFER);
    }

    public boolean hasEnoughBalance(BigDecimal amount) {
        return amount != null
                && amount.signum() > 0
                && currentBalance().subtract(currentFrozenBalance()).compareTo(amount) >= 0;
    }

    public Transaction recordFailedTransaction(
            BigDecimal amount, TransactionType type, String reason) {
        requirePositive(amount);
        Transaction transaction = Transaction.builder()
                .wallet(this)
                .amount(amount)
                .type(type)
                .status(TransactionStatus.FAILED)
                .description(reason)
                .build();
        addTransaction(transaction);
        return transaction;
    }

    private void requireAvailable(BigDecimal amount) {
        requirePositive(amount);
        if (!hasEnoughBalance(amount)) {
            throw new IllegalStateException("Insufficient available balance");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private BigDecimal currentBalance() {
        return balance == null ? BigDecimal.ZERO : balance;
    }

    private BigDecimal currentFrozenBalance() {
        return frozenBalance == null ? BigDecimal.ZERO : frozenBalance;
    }

    private Transaction record(BigDecimal amount, TransactionType type) {
        Transaction transaction = Transaction.builder()
                .wallet(this)
                .amount(amount)
                .type(type)
                .status(TransactionStatus.SUCCESS)
                .build();
        addTransaction(transaction);
        return transaction;
    }

    private void addTransaction(Transaction transaction) {
        if (transactions == null) {
            transactions = new ArrayList<>();
        }
        transactions.add(transaction);
    }
}
