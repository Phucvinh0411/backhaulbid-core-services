package iuh.fit.se.controller;

import iuh.fit.se.domain.dto.request.AdminSettingsRequest;
import iuh.fit.se.domain.dto.response.AdminSettingsResponse;
import iuh.fit.se.service.AdminSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSettingsController.class)
@Import(AdminSettingsControllerTest.MethodSecurityTestConfiguration.class)
class AdminSettingsControllerTest {
    private static final String ADMIN_ID = "8f14e45f-ea43-4a4f-b716-3f8f68f74201";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSettingsService adminSettingsService;

    @Test
    @WithMockUser(username = ADMIN_ID, roles = "ADMIN")
    void get_adminRole_returnsSettings() throws Exception {
        when(adminSettingsService.get("general"))
                .thenReturn(new AdminSettingsResponse("general", Map.of("platformName", "BackHaulBid"), null));

        mockMvc.perform(get("/api/v1/admin/settings/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("general"))
                .andExpect(jsonPath("$.values.platformName").value("BackHaulBid"));
    }

    @Test
    @WithMockUser(username = ADMIN_ID, roles = "ADMIN")
    void save_adminRole_persistsAuthenticatedAdminAndValues() throws Exception {
        UUID expectedAdminId = UUID.fromString(ADMIN_ID);
        ArgumentCaptor<AdminSettingsRequest> requestCaptor = ArgumentCaptor.forClass(AdminSettingsRequest.class);
        when(adminSettingsService.save(eq(expectedAdminId), eq("general"), requestCaptor.capture()))
                .thenReturn(new AdminSettingsResponse("general", Map.of("maintenanceMode", true), null));

        mockMvc.perform(put("/api/v1/admin/settings/general")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"values":{"maintenanceMode":true}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.maintenanceMode").value(true));

        assertThat(requestCaptor.getValue().getValues())
                .containsEntry("maintenanceMode", true);
    }

    @Test
    @WithMockUser(username = ADMIN_ID, roles = "CARRIER")
    void get_carrierRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/general"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings/general"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ADMIN_ID, roles = "ADMIN")
    void save_emptyValues_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/general")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"values\":{}}"))
                .andExpect(status().isBadRequest());
    }
}
