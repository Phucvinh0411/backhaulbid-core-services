package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.dto.response.WalletResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionPageResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionResponse;
import iuh.fit.se.walletservice.dto.response.AdminWalletSummaryResponse;
import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.mapper.WalletMapper;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Read-only wallet facade for the authenticated web portal. */
@Service
public class WalletQueryService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletMapper walletMapper;

    public WalletQueryService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            WalletMapper walletMapper) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.walletMapper = walletMapper;
    }

    @Transactional
    public WalletResponse getWallet(UUID accountId) {
        return walletMapper.toResponse(wallet(accountId));
    }

    @Transactional(readOnly = true)
    public WalletTransactionPageResponse getTransactions(UUID accountId, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }

        Page<Transaction> transactions = transactionRepository.findByWalletAccountId(
                accountId,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new WalletTransactionPageResponse(
                transactions.getContent().stream().map(walletMapper::toTransactionResponse).toList(),
                page,
                pageSize,
                transactions.getTotalElements(),
                transactions.getTotalPages());
    }

    @Transactional(readOnly = true)
    public WalletTransactionResponse getTransaction(
            UUID accountId,
            UUID transactionId) {
        Transaction transaction = transactionRepository.findByIdAndWalletAccountId(transactionId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        return walletMapper.toTransactionResponse(transaction);
    }

    @Transactional(readOnly = true)
    public WalletResponse getAdminWallet(UUID accountId) {
        return walletRepository.findByAccountId(accountId)
                .map(walletMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    @Transactional(readOnly = true)
    public AdminWalletSummaryResponse getAdminSummary() {
        return new AdminWalletSummaryResponse(
                transactionRepository.sumByStatusAndType(TransactionStatus.SUCCESS, TransactionType.AUCTION_FEE),
                transactionRepository.sumByStatusAndType(TransactionStatus.SUCCESS, TransactionType.DEPOSIT),
                transactionRepository.sumByStatusAndType(TransactionStatus.SUCCESS, TransactionType.WITHDRAW),
                transactionRepository.countByStatus(TransactionStatus.SUCCESS));
    }

    private Wallet wallet(UUID accountId) {
        return walletRepository.findByAccountId(accountId)
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder().accountId(accountId).build()));
    }
}
