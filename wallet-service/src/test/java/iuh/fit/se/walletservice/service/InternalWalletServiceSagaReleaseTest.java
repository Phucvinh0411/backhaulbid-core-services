package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.controller.InternalWalletController;
import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.domain.operation.InternalWalletOperation;
import iuh.fit.se.walletservice.dto.request.InternalWalletReleaseByKeyRequest;
import iuh.fit.se.walletservice.dto.request.InternalWalletReleaseRequest;
import iuh.fit.se.walletservice.dto.response.InternalWalletOperationResponse;
import iuh.fit.se.walletservice.mapper.InternalWalletOperationMapper;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalWalletServiceSagaReleaseTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InternalWalletOperationMapper mapper;

    private InternalWalletService service;
    private InternalWalletController controller;

    @BeforeEach
    void setUp() {
        service = new InternalWalletService(walletRepository, transactionRepository);
        controller = new InternalWalletController(service, mapper, "secret-internal-token");
    }

    @Test
    void releaseByHoldKeyReturnsEmptyWhenNoHoldTransactionExists() {
        String holdKey = "auction_creation_fee_" + UUID.randomUUID();
        when(transactionRepository.findByIdempotencyKey(holdKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceCode(holdKey)).thenReturn(Optional.empty());

        Optional<InternalWalletOperation> result = service.releaseByHoldKey(
                holdKey,
                new InternalWalletReleaseRequest(holdKey + ":saga-compensate"));

        assertThat(result).isEmpty();
        verifyNoInteractions(walletRepository);
    }

    @Test
    void releaseByHoldKeyUnfreezesOrphanedHoldByIdempotencyKeyAndSupportsColonsInKey() {
        UUID accountId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        String holdKey = UUID.randomUUID() + ":deposit:hold";
        String releaseKey = holdKey + ":saga-compensate";

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .balance(new BigDecimal("500000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        Transaction hold = wallet.freeze(new BigDecimal("50000.00"));
        hold.setId(holdId);
        hold.setIdempotencyKey(holdKey);
        hold.setReferenceCode(holdKey);

        when(transactionRepository.findByIdempotencyKey(holdKey)).thenReturn(Optional.of(hold));
        when(transactionRepository.findByIdempotencyKey(releaseKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceCode(releaseKey))
                .thenReturn(Optional.empty())
                .thenAnswer(inv -> wallet.getTransactions().stream()
                        .filter(tx -> releaseKey.equals(tx.getReferenceCode()))
                        .peek(tx -> {
                            if (tx.getId() == null) tx.setId(UUID.randomUUID());
                        })
                        .findFirst());
        when(transactionRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));

        Optional<InternalWalletOperation> result = service.releaseByHoldKey(
                holdKey,
                new InternalWalletReleaseRequest(releaseKey));

        assertThat(result).isPresent();
        assertThat(result.get().transaction().getType()).isEqualTo(TransactionType.UNFREEZE);
        assertThat(result.get().transaction().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(wallet.getFrozenBalance()).isEqualByComparingTo("0.00");
        assertThat(hold.getRemainingHoldAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void releaseByHoldKeyIsIdempotentOnRepeatedCalls() {
        UUID holdId = UUID.randomUUID();
        String holdKey = "auction_creation_fee_" + UUID.randomUUID();
        String releaseKey = holdKey + ":saga-compensate";

        Transaction hold = Transaction.builder()
                .id(holdId)
                .type(TransactionType.FREEZE)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("50000.00"))
                .remainingHoldAmount(BigDecimal.ZERO)
                .build();
        Transaction existingRelease = Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.UNFREEZE)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("50000.00"))
                .idempotencyKey(releaseKey)
                .build();

        when(transactionRepository.findByIdempotencyKey(holdKey)).thenReturn(Optional.of(hold));
        when(transactionRepository.findByIdempotencyKey(releaseKey)).thenReturn(Optional.of(existingRelease));

        Optional<InternalWalletOperation> result = service.releaseByHoldKey(
                holdKey,
                new InternalWalletReleaseRequest(releaseKey));

        assertThat(result).isPresent();
        assertThat(result.get().transaction().getId()).isEqualTo(existingRelease.getId());
        verifyNoInteractions(walletRepository);
    }

    @Test
    void releaseByHoldKeyDoesNotUnfreezeAgainWhenHoldAlreadyConsumed() {
        UUID holdId = UUID.randomUUID();
        String holdKey = "auction_creation_fee_" + UUID.randomUUID();
        String releaseKey = holdKey + ":saga-compensate";

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .balance(new BigDecimal("450000.00"))
                .frozenBalance(BigDecimal.ZERO)
                .build();
        Transaction consumedHold = Transaction.builder()
                .id(holdId)
                .wallet(wallet)
                .type(TransactionType.FREEZE)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("50000.00"))
                .remainingHoldAmount(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByIdempotencyKey(holdKey)).thenReturn(Optional.of(consumedHold));
        when(transactionRepository.findByIdempotencyKey(releaseKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceCode(releaseKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(consumedHold));

        Optional<InternalWalletOperation> result = service.releaseByHoldKey(
                holdKey,
                new InternalWalletReleaseRequest(releaseKey));

        assertThat(result).isPresent();
        assertThat(result.get().transaction().getId()).isEqualTo(holdId);
        assertThat(wallet.getFrozenBalance()).isEqualByComparingTo("0.00");
        verifyNoInteractions(walletRepository);
    }

    @Test
    void controllerReturnsNoOpWithNullTransactionIdAndRejectsInvalidToken() {
        String holdKey = "auction_creation_fee_" + UUID.randomUUID();
        var body = new InternalWalletReleaseByKeyRequest(holdKey, holdKey + ":saga-compensate");

        assertThatThrownBy(() -> controller.releaseByHoldKey("wrong-token", body))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        when(transactionRepository.findByIdempotencyKey(holdKey)).thenReturn(Optional.empty());
        when(transactionRepository.findByReferenceCode(holdKey)).thenReturn(Optional.empty());

        InternalWalletOperationResponse response = controller.releaseByHoldKey("secret-internal-token", body);
        assertThat(response.status()).isEqualTo("NO_OP");
        assertThat(response.transactionId()).isNull();
        assertThat(response.holdId()).isNull();
        assertThat(response.amount()).isEqualByComparingTo("0");
    }
}
