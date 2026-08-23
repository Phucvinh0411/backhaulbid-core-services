package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.dto.VehicleResponse;
import iuh.fit.se.fleetservice.service.VehicleService;
import iuh.fit.se.fleetservice.service.DriverProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({VehicleController.class, DriverController.class})
class PublicCarrierFleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VehicleService vehicleService;

    @MockBean
    private DriverProfileService driverProfileService;

    @Test
    void givenShipper_whenListCarrierVehicles_thenReturnsPublicVehicleData() throws Exception {
        UUID carrierId = UUID.randomUUID();
        when(vehicleService.listMine(carrierId, null)).thenReturn(List.of(
                Vehicle.builder().id(UUID.randomUUID()).carrierId(carrierId).licensePlate("51H-12345")
                        .vehicleType(VehicleType.TRUCK_MEDIUM).payloadCapacity(new BigDecimal("8.5"))
                        .status(VehicleStatus.VERIFIED).build()));

        mockMvc.perform(get("/api/v1/vehicles/carrier/{carrierId}", carrierId)
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-User-Role", "SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licensePlate").value("51H-12345"));
    }

    @Test
    void givenShipper_whenListCarrierDrivers_thenEndpointIsAvailable() throws Exception {
        UUID carrierId = UUID.randomUUID();
        when(driverProfileService.listMine(carrierId, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/drivers/carrier/{carrierId}", carrierId)
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-User-Role", "SHIPPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
