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

    /** Releases the frozen deposit after a registration is completed or cancelled. */
    @Transactional
    public InternalWalletOperation release(UUID holdId, InternalWalletReleaseRequest request) {
        Transaction existing = findSuccessfulOrFailed(request.idempotencyKey());
        if (existing != null) return operation(existing, null);

        Transaction hold = hold(holdId);
        Wallet wallet = hold.getWallet();
        try {
            Transaction transaction = wallet.unfreeze(hold.getAmount());
            transaction.setReferenceCode(request.idempotencyKey());
            transaction.setDescription("Release auction deposit hold " + holdId);
            walletRepository.saveAndFlush(wallet);
            return persistedOperation(request.idempotencyKey(), false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception.getMessage());
        }
    }

    /** Converts the frozen deposit into a penalty after a forfeiture decision. */
    @Transactional
    public InternalWalletOperation forfeit(UUID holdId, InternalWalletReleaseRequest request) {
        Transaction existing = findSuccessfulOrFailed(request.idempotencyKey());
        if (existing != null) return operation(existing, null);

        Transaction hold = hold(holdId);
        Wallet wallet = hold.getWallet();
        try {
            wallet.unfreeze(hold.getAmount());
            Transaction penalty = wallet.withdraw(hold.getAmount());
            penalty.setType(TransactionType.PENALTY);
            penalty.setReferenceCode(request.idempotencyKey());
            penalty.setDescription("Forfeit auction deposit hold " + holdId);
            walletRepository.saveAndFlush(wallet);
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

    private InternalWalletOperation operation(Transaction transaction, UUID holdId) {
        return new InternalWalletOperation(transaction, holdId);
    }

    private ResponseStatusException failure(String message) {
        return new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                message == null ? "Wallet operation failed" : message);
    }
}
