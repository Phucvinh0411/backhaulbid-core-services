package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import iuh.fit.se.walletservice.dto.request.InternalWalletReleaseRequest;
import iuh.fit.se.walletservice.dto.request.WithdrawalDecisionRequest;
import iuh.fit.se.walletservice.mapper.WithdrawalMapper;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import iuh.fit.se.walletservice.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletTransactionLabelingTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WithdrawalRequestRepository withdrawalRepository;
    @Mock private WithdrawalMapper withdrawalMapper;

    @Test
    void forfeitAuctionCreationFeeRecordsAuctionFeeTypeAndVietnameseDescription() {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .balance(new BigDecimal("1000000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        UUID holdId = UUID.randomUUID();
        Transaction hold = wallet.freeze(new BigDecimal("50000.00"));
        hold.setId(holdId);
        hold.setReferenceCode("auction_creation_fee_684d2e14-1111-2222-3333-444455556666");

        String forfeitKey = "auction_creation_fee_684d2e14-1111-2222-3333-444455556666:forfeit";
        when(transactionRepository.findByIdempotencyKey(forfeitKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceCode(forfeitKey))
                .thenReturn(Optional.empty())
                .thenAnswer(inv -> wallet.getTransactions().stream()
                        .filter(tx -> forfeitKey.equals(tx.getReferenceCode()))
                        .peek(tx -> {
                            if (tx.getId() == null) tx.setId(UUID.randomUUID());
                        })
                        .findFirst());
        when(transactionRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        InternalWalletService service = new InternalWalletService(walletRepository, transactionRepository);
        service.forfeit(holdId, new InternalWalletReleaseRequest(forfeitKey));

        Transaction recorded = wallet.getTransactions().get(wallet.getTransactions().size() - 1);
        assertThat(recorded.getType()).isEqualTo(TransactionType.AUCTION_FEE);
        assertThat(recorded.getDescription()).isEqualTo("Phí tạo phiên đấu giá #684D2E14");
    }

    @Test
    void rejectWithdrawalRecordsVietnameseReleaseReferenceAndDescription() {
        UUID accountId = UUID.randomUUID();
        UUID withdrawalId = UUID.fromString("12345678-aaaa-bbbb-cccc-111122223333");
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .balance(new BigDecimal("1000000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        wallet.freeze(new BigDecimal("500000.00"));

        WithdrawalRequest request = WithdrawalRequest.builder()
                .id(withdrawalId)
                .accountId(accountId)
                .amount(new BigDecimal("500000.00"))
                .bankName("BIDV")
                .bankAccountNumber("1234567890")
                .accountHolderName("TRAN VAN PHUC")
                .status(WithdrawalStatus.PENDING)
                .build();

        when(withdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(request);
        when(walletRepository.findByAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.save(any())).thenReturn(request);

        WithdrawalService service = new WithdrawalService(withdrawalRepository, walletRepository, withdrawalMapper);
        service.reject(withdrawalId, UUID.randomUUID(), new WithdrawalDecisionRequest("Sai thong tin"));

        Transaction release = wallet.getTransactions().get(wallet.getTransactions().size() - 1);
        assertThat(release.getType()).isEqualTo(TransactionType.UNFREEZE);
        assertThat(release.getReferenceCode()).isEqualTo("WITHDRAWAL_RELEASE:" + withdrawalId);
        assertThat(release.getDescription()).contains("#12345678");
    }

    @Test
    void approveWithdrawalSetsWithdrawalReleaseReferenceAndDescriptionBeforePayout() {
        UUID accountId = UUID.randomUUID();
        UUID withdrawalId = UUID.fromString("abcdef12-aaaa-bbbb-cccc-111122223333");
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .balance(new BigDecimal("1000000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        wallet.freeze(new BigDecimal("500000.00"));

        WithdrawalRequest request = WithdrawalRequest.builder()
                .id(withdrawalId)
                .accountId(accountId)
                .amount(new BigDecimal("500000.00"))
                .bankName("BIDV")
                .bankAccountNumber("1234567890")
                .accountHolderName("TRAN VAN PHUC")
                .status(WithdrawalStatus.PENDING)
                .build();

        when(withdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(request);
        when(walletRepository.findByAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.save(any())).thenReturn(request);

        WithdrawalService service = new WithdrawalService(withdrawalRepository, walletRepository, withdrawalMapper);
        service.approve(withdrawalId, UUID.randomUUID());

        Transaction release = wallet.getTransactions().get(wallet.getTransactions().size() - 2);
        Transaction payout = wallet.getTransactions().get(wallet.getTransactions().size() - 1);
        assertThat(release.getType()).isEqualTo(TransactionType.UNFREEZE);
        assertThat(release.getReferenceCode()).isEqualTo("WITHDRAWAL_RELEASE:" + withdrawalId);
        assertThat(release.getDescription()).isEqualTo("Giải phóng tạm giữ chi trả yêu cầu rút tiền #ABCDEF12");
        assertThat(payout.getType()).isEqualTo(TransactionType.WITHDRAW);
        assertThat(payout.getReferenceCode()).isEqualTo("WITHDRAWAL:" + withdrawalId);
    }
}
