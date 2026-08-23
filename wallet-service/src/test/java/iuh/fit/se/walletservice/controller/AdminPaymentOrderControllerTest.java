package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.response.PaymentOrderPageResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import iuh.fit.se.walletservice.service.PaymentOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPaymentOrderControllerTest {
    private final PaymentOrderService service = mock(PaymentOrderService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminPaymentOrderController(service)).build();

    @Test
    void list_requiresAdminAndReturnsAuditPage() throws Exception {
        when(service.listAdmin("PAID", 1, 20)).thenReturn(new PaymentOrderPageResponse(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/sepay/top-ups")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(service).listAdmin(eq("PAID"), eq(1), eq(20));
    }

    @Test
    void list_rejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sepay/top-ups")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "SHIPPER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_returnsPaymentOrderDetail() throws Exception {
        when(service.getAdmin("BBTOPUP_001")).thenReturn(new PaymentOrderResponse(
                null, null, "BBTOPUP_001", null, "SEPAY", "PAID", null, null, null, null, null, null));

        mockMvc.perform(get("/api/v1/admin/sepay/top-ups/BBTOPUP_001")
                        .header("X-User-Id", "99999999-9999-9999-9999-999999999999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("BBTOPUP_001"));
    }
}
