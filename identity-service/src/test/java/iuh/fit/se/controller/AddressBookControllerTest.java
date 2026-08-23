package iuh.fit.se.controller;

import iuh.fit.se.domain.dto.response.AddressResponse;
import iuh.fit.se.service.AddressBookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressBookController.class)
@Import(AddressBookControllerTest.MethodSecurityTestConfiguration.class)
class AddressBookControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfiguration {
        @Bean
        String addressBookControllerTestMarker() {
            return "address-book-test";
        }
    }

    private static final String ACCOUNT_ID = "8f14e45f-ea43-4a4f-b716-3f8f68f74201";
    private static final UUID ADDRESS_ID = UUID.fromString("ca978112-ca1b-4dca-bac2-31b39a23dc4d");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddressBookService addressBookService;

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void list_authenticatedShipper_returnsOwnAddresses() throws Exception {
        when(addressBookService.list(UUID.fromString(ACCOUNT_ID)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ADDRESS_ID.toString()))
                .andExpect(jsonPath("$[0].label").value("Kho Bình Dương"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "SHIPPER")
    void create_validAddress_returnsCreated() throws Exception {
        when(addressBookService.create(eq(UUID.fromString(ACCOUNT_ID)), any()))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/addresses")
                        .with(csrf())
                        .contentType("application/json")
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactPhone").value("0912345678"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = "ADMIN")
    void list_adminRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isUnauthorized());
    }

    private static AddressResponse response() {
        return new AddressResponse(
                ADDRESS_ID,
                UUID.fromString(ACCOUNT_ID),
                "Kho Bình Dương",
                "Nguyễn Văn A",
                "0912345678",
                "Bình Dương",
                "Đường số 8, KCN VSIP 1",
                null,
                null);
    }

    private static String validJson() {
        return """
                {
                  "label": "Kho Bình Dương",
                  "contactName": "Nguyễn Văn A",
                  "contactPhone": "0912345678",
                  "province": "Bình Dương",
                  "detail": "Đường số 8, KCN VSIP 1"
                }
                """;
    }
}
