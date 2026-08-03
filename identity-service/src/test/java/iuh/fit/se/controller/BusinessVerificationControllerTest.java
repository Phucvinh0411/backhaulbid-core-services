package iuh.fit.se.controller;

import iuh.fit.se.dto.BusinessLookupResponse;
import iuh.fit.se.dto.BusinessVerificationResponse;
import iuh.fit.se.dto.BusinessVerificationPageResponse;
import iuh.fit.se.service.BusinessVerificationService;
import iuh.fit.se.service.CompanyVerificationService;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessVerificationController.class)
@Import(BusinessVerificationControllerTest.MethodSecurityTestConfiguration.class)
class BusinessVerificationControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
        @Bean
        BusinessVerificationMapper businessVerificationMapper() {
            return Mappers.getMapper(BusinessVerificationMapper.class);
        }
    }

    private static final String ACCOUNT_ID = "8f14e45f-ea43-4a4f-b716-3f8f68f74201";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusinessVerificationService businessVerificationService;

    @MockBean
    private CompanyVerificationService companyVerificationService;

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void lookup_authenticatedBusinessUser_returnsBusinessData() throws Exception {
        BusinessLookupResponse response = BusinessLookupResponse.builder()
                .taxCode("0312345678")
                .companyName("BackHaulBid Logistics")
                .valid(true)
                .message("Business is valid")
                .build();
        when(businessVerificationService.lookupByTaxCode("0312345678")).thenReturn(response);

        mockMvc.perform(get("/api/v1/business-verifications/lookup/0312345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxCode").value("0312345678"))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void lookup_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/business-verifications/lookup/0312345678"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void lookup_invalidTaxCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/business-verifications/lookup/not-a-tax-code"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void submit_validBusinessLicense_returnsPendingVerification() throws Exception {
        MockMultipartFile license = new MockMultipartFile(
                "businessLicense",
                "dang-ky-kinh-doanh.pdf",
                "application/pdf",
                "%PDF-1.7 sample".getBytes()
        );
        BusinessVerificationResponse response = BusinessVerificationResponse.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .taxCode("0312345678")
                .companyName("BackHaulBid Logistics")
                .status("PENDING")
                .businessLicenseFilename("dang-ky-kinh-doanh.pdf")
                .build();
        when(companyVerificationService.submit(
                eq(ACCOUNT_ID), eq("0312345678"), eq("Nguyễn Văn A"), any(), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/business-verifications")
                        .file(license)
                        .param("taxCode", "0312345678")
                        .param("ekycRepresentativeName", "Nguyễn Văn A")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.businessLicenseFilename")
                        .value("dang-ky-kinh-doanh.pdf"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "CARRIER")
    void current_submittedBusiness_returnsPersistedStatus() throws Exception {
        BusinessVerificationResponse response = BusinessVerificationResponse.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .taxCode("0312345678")
                .companyName("BackHaulBid Logistics")
                .status("PENDING")
                .build();
        when(companyVerificationService.findCurrent(ACCOUNT_ID))
                .thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/business-verifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.taxCode").value("0312345678"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void current_withoutSubmission_returnsNotSubmitted() throws Exception {
        when(companyVerificationService.findCurrent(ACCOUNT_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/business-verifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_SUBMITTED"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "ADMIN")
    void list_admin_returnsPendingBusinessVerifications() throws Exception {
        BusinessVerificationResponse response = BusinessVerificationResponse.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .taxCode("0312345678")
                .companyName("BackHaulBid Logistics")
                .status("PENDING")
                .build();
        when(companyVerificationService.findByStatus("PENDING", 0, 20))
                .thenReturn(BusinessVerificationPageResponse.builder()
                        .items(List.of(response))
                        .page(0)
                        .pageSize(20)
                        .totalItems(1)
                        .totalPages(1)
                        .build());

        mockMvc.perform(get("/api/v1/business-verifications")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].taxCode").value("0312345678"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "ADMIN")
    void review_adminApprovesPendingBusiness() throws Exception {
        UUID verificationId =
                UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d");
        BusinessVerificationResponse response = BusinessVerificationResponse.builder()
                .id(verificationId)
                .status("VERIFIED")
                .build();
        when(companyVerificationService.review(
                eq(verificationId), eq("APPROVE"), eq(null)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/business-verifications/{id}", verificationId)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision": "APPROVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void list_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/business-verifications")
                        .param("status", "PENDING"))
                .andExpect(status().isForbidden());
    }
}
