package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.dto.response.WalletTransactionResponse;
import iuh.fit.se.walletservice.mapper.WalletMapper;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletQueryMissingApiTest {
    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletMapper walletMapper;

    private WalletQueryService service;

    @BeforeEach
    void setUp() {
        service = new WalletQueryService(walletRepository, transactionRepository, walletMapper);
    }

    @Test
    void givenTransactionOwnedByAccount_whenReadingDetail_thenReturnsTransaction() {
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = new Transaction();
        WalletTransactionResponse expected = new WalletTransactionResponse(
                transactionId, null, "DEPOSIT", "SUCCESS", "BANK_TRANSFER", "REF-1", "Top up", null);
        when(transactionRepository.findByIdAndWalletAccountId(transactionId, accountId))
                .thenReturn(Optional.of(transaction));
        when(walletMapper.toTransactionResponse(transaction)).thenReturn(expected);

        assertThat(service.getTransaction(accountId, transactionId)).isSameAs(expected);
    }

    @Test
    void givenTransactionOwnedByAnotherAccount_whenReadingDetail_thenReturnsNotFound() {
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndWalletAccountId(transactionId, accountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(accountId, transactionId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(404);
    }
}
