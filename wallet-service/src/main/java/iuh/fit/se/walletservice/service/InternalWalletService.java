package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.domain.operation.InternalWalletOperation;
import iuh.fit.se.walletservice.dto.request.InternalWalletOperationRequest;
import iuh.fit.se.walletservice.dto.request.InternalWalletReleaseRequest;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Coordinates wallet mutations while keeping idempotency and transaction rules in one place. */
@Service
public class InternalWalletService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public InternalWalletService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Freezes the deposit amount. A repeated idempotency key returns the
     * existing transaction so a network retry cannot freeze money twice.
     */
    @Transactional
    public InternalWalletOperation hold(UUID accountId, InternalWalletOperationRequest request) {
        Transaction existing = findSuccessfulOrFailed(request.idempotencyKey());
        if (existing != null) return operation(existing, existing.getId());

        Wallet wallet = wallet(accountId);
        try {
            Transaction transaction = wallet.freeze(request.amount());
            return saveSuccess(wallet, transaction, request, TransactionType.FREEZE, transaction.getId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    /** Charges the mandatory auction participation fee exactly once. */
    @Transactional
    public InternalWalletOperation charge(UUID accountId, InternalWalletOperationRequest request) {
        Transaction existing = findSuccessfulOrFailed(request.idempotencyKey());
        if (existing != null) return operation(existing, null);

        Wallet wallet = wallet(accountId);
        try {
            Transaction transaction = wallet.withdraw(request.amount());
            return saveSuccess(wallet, transaction, request, TransactionType.AUCTION_FEE, null);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    /** Credits a previously charged auction fee back to the shipper exactly once. */
    @Transactional
    public InternalWalletOperation refund(UUID accountId, InternalWalletOperationRequest request) {
        Transaction existing = findByOperationKey(request.idempotencyKey());
        if (existing != null) return operation(existing, null);

        Wallet wallet = wallet(accountId);
        try {
            Transaction transaction = wallet.deposit(request.amount());
            return saveSuccess(wallet, transaction, request, TransactionType.REFUND, null);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    /** Releases the frozen deposit after a registration is completed or cancelled. */
    @Transactional
    public InternalWalletOperation release(UUID holdId, InternalWalletReleaseRequest request) {
        Transaction existing = findByOperationKey(request.idempotencyKey());
        if (existing != null) return operation(existing, null);

        Transaction hold = holdForUpdate(holdId);
        Wallet wallet = hold.getWallet();
        try {
            BigDecimal remaining = remainingHold(hold);
            if (remaining.signum() == 0) {
                return operation(hold, holdId);
            }
            Transaction transaction = wallet.unfreeze(remaining);
            transaction.setReferenceCode(request.idempotencyKey());
            transaction.setDescription("Release auction deposit hold " + holdId);
            transaction.setIdempotencyKey(request.idempotencyKey());
            hold.setRemainingHoldAmount(BigDecimal.ZERO);
            walletRepository.saveAndFlush(wallet);
            return persistedOperation(request.idempotencyKey(), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    /** Converts the frozen deposit into a penalty after a forfeiture decision. */
    @Transactional
    public InternalWalletOperation forfeit(UUID holdId, InternalWalletReleaseRequest request) {
        Transaction existing = findByOperationKey(request.idempotencyKey());
        if (existing != null) return operation(existing, null);

        Transaction hold = holdForUpdate(holdId);
        Wallet wallet = hold.getWallet();
        try {
            BigDecimal remaining = remainingHold(hold);
            if (remaining.signum() == 0) {
                throw new IllegalStateException("Deposit hold has no remaining amount");
            }
            wallet.unfreeze(remaining);
            Transaction penalty = wallet.withdraw(remaining);
            penalty.setType(TransactionType.PENALTY);
            penalty.setReferenceCode(request.idempotencyKey());
            penalty.setDescription("Forfeit auction deposit hold " + holdId);
            penalty.setIdempotencyKey(request.idempotencyKey());
            hold.setRemainingHoldAmount(BigDecimal.ZERO);
            walletRepository.saveAndFlush(wallet);
            return persistedOperation(request.idempotencyKey(), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    /** Transfers a single incremental late-delivery penalty directly from the frozen deposit. */
    @Transactional
    public InternalWalletOperation settleHeldDeposit(UUID holdId,
            iuh.fit.se.walletservice.dto.request.InternalWalletSettlementRequest request) {
        Transaction existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) return operation(existing, null);

        Transaction hold = holdForUpdate(holdId);
        Wallet source = hold.getWallet();
        Wallet recipient = walletRepository.findByAccountIdForUpdate(request.recipientAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient wallet not found"));
        try {
            List<Transaction> transfers = source.transferFromHold(recipient, hold, request.amount());
            Transaction debit = transfers.get(0);
            Transaction credit = transfers.get(1);
            debit.setIdempotencyKey(request.idempotencyKey());
            debit.setReferenceCode(request.idempotencyKey());
            debit.setDescription("Bồi thường giao hàng trễ bậc " + request.tier() + " chuyến " + request.tripId());
            credit.setIdempotencyKey(request.idempotencyKey() + ":credit");
            credit.setReferenceCode(request.idempotencyKey() + ":credit");
            credit.setDescription("Nhận bồi thường giao hàng trễ bậc " + request.tier() + " chuyến " + request.tripId());
            walletRepository.saveAndFlush(source);
            walletRepository.saveAndFlush(recipient);
            return persistedOperation(request.idempotencyKey(), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    private InternalWalletOperation saveSuccess(
            Wallet wallet,
            Transaction transaction,
            InternalWalletOperationRequest request,
            TransactionType type,
            UUID holdId) {
        transaction.setType(type);
        transaction.setReferenceCode(request.idempotencyKey());
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setDescription(request.purpose() + " for auction " + request.auctionId()
                + ", registration " + request.registrationId());
        walletRepository.saveAndFlush(wallet);
        return persistedOperation(request.idempotencyKey(), holdId != null);
    }

    /**
     * Reads the flushed transaction before mapping the HTTP response so the
     * generated UUID is always present for the bidding service.
     */
    private InternalWalletOperation persistedOperation(String referenceCode, boolean exposeHoldId) {
        Transaction persisted = transactionRepository.findByReferenceCode(referenceCode)
                .orElseThrow(() -> new IllegalStateException("Wallet transaction was not persisted"));
        return operation(persisted, exposeHoldId ? persisted.getId() : null);
    }

    private Transaction findSuccessfulOrFailed(String referenceCode) {
        return transactionRepository.findByReferenceCode(referenceCode).orElse(null);
    }

    private Transaction findByOperationKey(String key) {
        return transactionRepository.findByIdempotencyKey(key)
                .orElseGet(() -> findSuccessfulOrFailed(key));
    }

    private Wallet wallet(UUID accountId) {
        return walletRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private Transaction hold(UUID holdId) {
        Transaction hold = transactionRepository.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet hold not found"));
        if (hold.getType() != TransactionType.FREEZE || hold.getStatus() != TransactionStatus.SUCCESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet hold is not active");
        }
        return hold;
    }

    private Transaction holdForUpdate(UUID holdId) {
        Transaction hold = transactionRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet hold not found"));
        if (hold.getType() != TransactionType.FREEZE || hold.getStatus() != TransactionStatus.SUCCESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet hold is not active");
        }
        return hold;
    }

    private BigDecimal remainingHold(Transaction hold) {
        return hold.getRemainingHoldAmount() == null ? hold.getAmount() : hold.getRemainingHoldAmount();
    }

    private InternalWalletOperation operation(Transaction transaction, UUID holdId) {
        return new InternalWalletOperation(transaction, holdId);
    }

    private ResponseStatusException failure(String message) {
        return new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                message == null ? "Wallet operation failed" : message);
    }
}
