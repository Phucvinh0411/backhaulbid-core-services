package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.service.PaymentOrderService;
import iuh.fit.se.walletservice.dto.response.PaymentOrderPageResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SepayPaymentControllerTest {
    private final PaymentOrderService paymentOrderService = mock(PaymentOrderService.class);
    private final SepayPaymentController controller = new SepayPaymentController(paymentOrderService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void receiveIpn_withProviderCredentials_returnsSuccessResponse() {
        ResponseEntity<Map<String, Boolean>> response = controller.receiveIpn(
                "provider-secret",
                null,
                null,
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
        verify(paymentOrderService).handleIpn("provider-secret", null);
    }

    @Test
    void receiveIpn_overHttp_returnsSuccessBodyAndForwardsPayload() throws Exception {
        mockMvc.perform(post("/api/v1/payments/sepay/ipn")
                        .header("X-Api-Key", "provider-secret")
                        .contentType("application/json")
                        .content("{\"id\":\"tx-001\",\"code\":\"BBTOPUP_123_ab12cd34\",\"transferAmount\":\"50000\",\"transferType\":\"in\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentOrderService).handleIpn(eq("provider-secret"), any());
    }

    @Test
    void listTopUps_overHttp_forwardsAuthenticatedAccountAndPagination() throws Exception {
        when(paymentOrderService.listMine(any(), eq(2), eq(10)))
                .thenReturn(new PaymentOrderPageResponse(java.util.List.of(), 2, 10, 0, 0));

        mockMvc.perform(get("/api/v1/payments/sepay/top-ups")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(paymentOrderService).listMine(
                eq(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")), eq(2), eq(10));
    }

    @Test
    void cancelTopUp_overHttp_forwardsInvoiceAndAuthenticatedAccount() throws Exception {
        when(paymentOrderService.cancelTopUp(any(), eq("BBTOPUP_cancel_001")))
                .thenReturn(new PaymentOrderResponse(
                        null, null, "BBTOPUP_cancel_001", null, "SEPAY", "CANCELLED",
                        null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/payments/sepay/top-ups/BBTOPUP_cancel_001/cancel")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(paymentOrderService).cancelTopUp(
                eq(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")),
                eq("BBTOPUP_cancel_001"));
    }
}
