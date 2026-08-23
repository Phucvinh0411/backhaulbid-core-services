package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import iuh.fit.se.walletservice.dto.request.WithdrawalDecisionRequest;
import iuh.fit.se.walletservice.dto.request.WithdrawalRequestDto;
import iuh.fit.se.walletservice.dto.response.WithdrawalPageResponse;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import iuh.fit.se.walletservice.mapper.WithdrawalMapper;
import iuh.fit.se.walletservice.repository.WalletRepository;
import iuh.fit.se.walletservice.repository.WithdrawalRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Coordinates manual withdrawals and the wallet hold released by each decision. */
@Service
public class WithdrawalService {
    private final WithdrawalRequestRepository withdrawalRepository;
    private final WalletRepository walletRepository;
    private final WithdrawalMapper withdrawalMapper;

    public WithdrawalService(
            WithdrawalRequestRepository withdrawalRepository,
            WalletRepository walletRepository,
            WithdrawalMapper withdrawalMapper) {
        this.withdrawalRepository = withdrawalRepository;
        this.walletRepository = walletRepository;
        this.withdrawalMapper = withdrawalMapper;
    }

    @Transactional
    public WithdrawalResponse create(UUID accountId, WithdrawalRequestDto request) {
        validateWholeAmount(request.amount());
        Wallet wallet = wallet(accountId);
        WithdrawalRequest withdrawal = withdrawalRepository.saveAndFlush(WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(request.amount().setScale(2))
                .bankName(request.bankName().trim())
                .bankAccountNumber(request.bankAccountNumber().trim())
                .accountHolderName(request.accountHolderName().trim().toUpperCase())
                .status(WithdrawalStatus.PENDING)
                .build());

        Transaction hold = wallet.freeze(withdrawal.getAmount());
        hold.setReferenceCode("WITHDRAWAL_HOLD:" + withdrawal.getId());
        hold.setDescription("Hold for withdrawal request " + withdrawal.getId());
        walletRepository.saveAndFlush(wallet);
        return withdrawalMapper.toResponse(withdrawal);
    }

    @Transactional(readOnly = true)
    public WithdrawalPageResponse mine(UUID accountId, int page, int pageSize) {
        return page(withdrawalRepository.findByAccountId(accountId, pageable(page, pageSize)), page, pageSize);
    }

    @Transactional(readOnly = true)
    public WithdrawalPageResponse pending(String status, int page, int pageSize) {
        WithdrawalStatus filter = status == null || status.isBlank()
                ? WithdrawalStatus.PENDING
                : parseStatus(status);
        return page(withdrawalRepository.findByStatus(filter, pageable(page, pageSize)), page, pageSize);
    }

    @Transactional
    public WithdrawalResponse approve(UUID withdrawalId, UUID adminId) {
        WithdrawalRequest withdrawal = pendingRequest(withdrawalId);
        Wallet wallet = wallet(withdrawal.getAccountId());

        wallet.unfreeze(withdrawal.getAmount());
        Transaction payout = wallet.withdraw(withdrawal.getAmount());
        payout.setType(TransactionType.WITHDRAW);
        payout.setReferenceCode("WITHDRAWAL:" + withdrawal.getId());
        payout.setDescription("Approved manual withdrawal " + withdrawal.getId());
        walletRepository.saveAndFlush(wallet);

        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setProcessedBy(adminId);
        withdrawal.setProcessedAt(Instant.now());
        return withdrawalMapper.toResponse(withdrawalRepository.save(withdrawal));
    }

    @Transactional
    public WithdrawalResponse reject(UUID withdrawalId, UUID adminId, WithdrawalDecisionRequest decision) {
        WithdrawalRequest withdrawal = pendingRequest(withdrawalId);
        Wallet wallet = wallet(withdrawal.getAccountId());
        wallet.unfreeze(withdrawal.getAmount());
        walletRepository.saveAndFlush(wallet);

        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setProcessedBy(adminId);
        withdrawal.setProcessedAt(Instant.now());
        withdrawal.setRejectionReason(decision == null ? "Rejected by administrator" : decision.reason());
        return withdrawalMapper.toResponse(withdrawalRepository.save(withdrawal));
    }

    private WithdrawalRequest pendingRequest(UUID withdrawalId) {
        WithdrawalRequest withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId);
        if (withdrawal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal request not found");
        }
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal request was already processed");
        }
        return withdrawal;
    }

    private Wallet wallet(UUID accountId) {
        return walletRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private WithdrawalPageResponse page(Page<WithdrawalRequest> result, int page, int pageSize) {
        return new WithdrawalPageResponse(
                result.getContent().stream().map(withdrawalMapper::toResponse).toList(),
                page,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages());
    }

    private PageRequest pageable(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private WithdrawalStatus parseStatus(String status) {
        try {
            return WithdrawalStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid withdrawal status");
        }
    }

    private static void validateWholeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Withdrawal amount must be a positive whole VND amount");
        }
    }
}
