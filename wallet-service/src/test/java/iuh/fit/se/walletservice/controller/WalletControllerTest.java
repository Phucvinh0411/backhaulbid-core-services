package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.response.WalletResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionPageResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionResponse;
import iuh.fit.se.walletservice.service.WalletQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

class WalletControllerTest {
    private final WalletQueryService walletQueryService = mock(WalletQueryService.class);
    private final WalletController controller = new WalletController(walletQueryService);

    @Test
    void getMyWallet_delegatesToQueryServiceForNewAccount() {
        UUID accountId = UUID.randomUUID();
        WalletResponse expected = new WalletResponse(accountId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        when(walletQueryService.getWallet(accountId)).thenReturn(expected);

        assertThat(controller.getMyWallet(accountId.toString())).isSameAs(expected);
        verify(walletQueryService).getWallet(accountId);
    }

    @Test
    void getMyTransactions_usesRequestedPagination() {
        UUID accountId = UUID.randomUUID();
        WalletTransactionPageResponse expected = new WalletTransactionPageResponse(java.util.List.of(), 1, 100, 0, 0);
        when(walletQueryService.getTransactions(accountId, 1, 100)).thenReturn(expected);

        assertThat(controller.getMyTransactions(accountId.toString(), 1, 100)).isSameAs(expected);
        verify(walletQueryService).getTransactions(accountId, 1, 100);
    }

    @Test
    void getMyWallet_withoutAuthenticatedAccount_returnsUnauthorized() {
        assertThatThrownBy(() -> controller.getMyWallet(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void getMyTransaction_delegatesToQueryServiceForAuthenticatedAccount() {
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        WalletTransactionResponse expected = new WalletTransactionResponse(
                transactionId, BigDecimal.ONE, "DEPOSIT", "SUCCESS", null, null, null, null);
        when(walletQueryService.getTransaction(accountId, transactionId)).thenReturn(expected);

        assertThat(controller.getMyTransaction(accountId.toString(), transactionId)).isSameAs(expected);
        verify(walletQueryService).getTransaction(accountId, transactionId);
    }
}
