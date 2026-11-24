package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import iuh.fit.se.walletservice.dto.response.AdminWalletSummaryResponse;
import iuh.fit.se.walletservice.mapper.WalletMapper;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import iuh.fit.se.walletservice.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletQueryServiceTest {
    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private WalletMapper walletMapper;

    @InjectMocks private WalletQueryService walletQueryService;

    @Test
    void getAdminSummaryIncludesPendingWithdrawalsCount() {
        when(transactionRepository.sumByStatusAndType(TransactionStatus.SUCCESS, TransactionType.AUCTION_FEE))
                .thenReturn(new BigDecimal("4710000.00"));
        when(transactionRepository.sumByStatusAndType(TransactionStatus.SUCCESS, TransactionType.DEPOSIT))
                .thenReturn(new BigDecimal("100000000.00"));
        when(transactionRepository.sumByStatusAndType(TransactionStatus.SUCCESS, TransactionType.WITHDRAW))
                .thenReturn(new BigDecimal("64000000.00"));
        when(transactionRepository.countByStatus(TransactionStatus.SUCCESS)).thenReturn(380L);
        when(withdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING)).thenReturn(4L);

        AdminWalletSummaryResponse summary = walletQueryService.getAdminSummary();

        assertEquals(new BigDecimal("4710000.00"), summary.auctionFees());
        assertEquals(new BigDecimal("64000000.00"), summary.withdrawals());
        assertEquals(380L, summary.successfulTransactions());
        assertEquals(4L, summary.pendingWithdrawalsCount());
    }
}
