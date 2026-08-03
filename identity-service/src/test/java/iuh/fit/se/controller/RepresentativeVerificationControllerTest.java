package iuh.fit.se.controller;

import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.dto.EkycSubmitRequest;
import iuh.fit.se.service.EkycService;
import iuh.fit.se.mapper.RepresentativeVerificationMapper;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepresentativeVerificationController.class)
@Import(RepresentativeVerificationControllerTest.MethodSecurityTestConfiguration.class)
class RepresentativeVerificationControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
        @Bean
        RepresentativeVerificationMapper representativeVerificationMapper() {
            return Mappers.getMapper(RepresentativeVerificationMapper.class);
        }
    }

    private static final String ACCOUNT_ID = "8f14e45f-ea43-4a4f-b716-3f8f68f74201";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EkycService ekycService;

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void submit_authenticatedBusinessUser_savesRepresentativeVerification() throws Exception {
        EkycVerification savedVerification = EkycVerification.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .identityNumber("079123456789")
                .fullName("Nguyễn Văn A")
                .livenessPassed(true)
                .faceMatchScore(98.5)
                .status(VerificationStatus.VERIFIED)
                .build();
        ArgumentCaptor<EkycSubmitRequest> requestCaptor =
                ArgumentCaptor.forClass(EkycSubmitRequest.class);
        when(ekycService.processEkyc(eq(ACCOUNT_ID), requestCaptor.capture()))
                .thenReturn(savedVerification);

        mockMvc.perform(post("/api/v1/representative-verifications")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        assertThat(requestCaptor.getValue().getFaceMatched()).isTrue();
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void current_authenticatedUser_returnsOwnRepresentativeStatus() throws Exception {
        EkycVerification verification = EkycVerification.builder()
                .id(UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d"))
                .identityNumber("079123456789")
                .fullName("Nguyễn Văn A")
                .livenessPassed(true)
                .faceMatchScore(98.5)
                .status(VerificationStatus.VERIFIED)
                .build();
        when(ekycService.findByAccountId(ACCOUNT_ID))
                .thenReturn(java.util.Optional.of(verification));

        mockMvc.perform(get("/api/v1/representative-verifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));
    }

    @Test
    void submit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/representative-verifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "ADMIN")
    void submit_nonBusinessRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/representative-verifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());
    }

    private String validRequestJson() {
        return """
                {
                  "identityNumber": "079123456789",
                  "fullName": "Nguyễn Văn A",
                  "frontImageUrl": "idg/front-hash",
                  "backImageUrl": "idg/back-hash",
                  "selfieImageUrl": "idg/face-hash",
                  "ocrPassed": true,
                  "documentLivenessPassed": true,
                  "documentAuthenticityPassed": true,
                  "livenessPassed": true,
                  "faceMatched": true,
                  "faceMatchScore": 98.5
                }
                """;
    }
}
