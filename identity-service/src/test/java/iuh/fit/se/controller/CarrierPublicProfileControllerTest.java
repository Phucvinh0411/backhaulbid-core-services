package iuh.fit.se.controller;

import iuh.fit.se.dto.CarrierPublicProfileResponse;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import iuh.fit.se.service.BusinessVerificationService;
import iuh.fit.se.service.CarrierPublicProfileService;
import iuh.fit.se.service.CompanyVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessVerificationController.class)
@Import(CarrierPublicProfileControllerTest.MethodSecurityTestConfiguration.class)
class CarrierPublicProfileControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusinessVerificationService businessVerificationService;

    @MockBean
    private CompanyVerificationService companyVerificationService;

    @MockBean
    private BusinessVerificationMapper businessVerificationMapper;

    @MockBean
    private CarrierPublicProfileService carrierPublicProfileService;

    @Test
    @WithMockUser(username = "8f14e45f-ea43-4a4f-b716-3f8f68f74201", roles = "SHIPPER")
    void givenShipper_whenGetCarrierProfile_thenReturnsPublicData() throws Exception {
        UUID carrierId = UUID.randomUUID();
        when(carrierPublicProfileService.get(carrierId)).thenReturn(new CarrierPublicProfileResponse(
                carrierId, "carrier@test.local", "0900000000", "Nhà xe thật", "Bình Dương",
                "Nguyễn Văn A", "0123456789", "VERIFIED"));

        mockMvc.perform(get("/api/v1/business-verifications/public/{accountId}", carrierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(carrierId.toString()))
                .andExpect(jsonPath("$.companyName").value("Nhà xe thật"));
    }

    @Test
    @WithMockUser(username = "8f14e45f-ea43-4a4f-b716-3f8f68f74201", roles = "ADMIN")
    void givenAdmin_whenGetCarrierProfile_thenAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/business-verifications/public/{accountId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
