package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.response.WalletResponse;
import iuh.fit.se.walletservice.service.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminWalletControllerTest {
    private final WalletQueryService service = mock(WalletQueryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminWalletController(service)).build();

    @Test
    void getWallet_requiresAdminAndReturnsWallet() throws Exception {
        UUID accountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(service.getAdminWallet(accountId)).thenReturn(
                new WalletResponse(accountId, new BigDecimal("12000000"), BigDecimal.ZERO, new BigDecimal("12000000"), null));

        mockMvc.perform(get("/api/v1/admin/wallets/{accountId}", accountId)
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));

        verify(service).getAdminWallet(eq(accountId));
    }
}
