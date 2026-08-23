package iuh.fit.se.fleetservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import iuh.fit.se.fleetservice.service.EmptyRouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmptyRouteController.class)
class EmptyRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmptyRouteService emptyRouteService;

    @Test
    void givenCarrier_whenListMine_thenOnlyOwnedRoutesAreReturned() throws Exception {
        UUID carrierId = UUID.randomUUID();
        when(emptyRouteService.listMine(carrierId.toString())).thenReturn(List.of(
                EmptyRoute.builder().id(UUID.randomUUID()).companyId(carrierId.toString()).truckId("51H-12345")
                        .expectedEmptyTime(LocalDateTime.now().plusHours(2)).latitude(10.8).longitude(106.7)
                        .status(EmptyRouteStatus.PENDING).build()
        ));

        mockMvc.perform(get("/api/v1/empty-routes")
                        .header("X-User-Id", carrierId)
                        .header("X-User-Role", "CARRIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyId").value(carrierId.toString()));
    }

    @Test
    void givenShipper_whenListMine_thenAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/empty-routes")
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-User-Role", "SHIPPER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenCarrier_whenCreateRoute_thenOwnerHeaderIsForwardedToService() throws Exception {
        UUID carrierId = UUID.randomUUID();
        EmptyRoute created = EmptyRoute.builder().id(UUID.randomUUID()).companyId(carrierId.toString()).truckId("51H-12345")
                .expectedEmptyTime(LocalDateTime.now().plusHours(2)).latitude(10.8).longitude(106.7)
                .status(EmptyRouteStatus.PENDING).build();
        when(emptyRouteService.createEmptyRoute(any(), eq(carrierId.toString()))).thenReturn(created);

        String request = objectMapper.writeValueAsString(new Object() {
            public final String truckId = "51H-12345";
            public final String companyId = "spoofed-company";
            public final LocalDateTime expectedEmptyTime = LocalDateTime.now().plusHours(2);
            public final double latitude = 10.8;
            public final double longitude = 106.7;
        });

        mockMvc.perform(post("/api/v1/empty-routes")
                        .header("X-User-Id", carrierId)
                        .header("X-User-Role", "CARRIER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(carrierId.toString()));

        verify(emptyRouteService).createEmptyRoute(any(), eq(carrierId.toString()));
    }
}
