package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.repository.EmptyRouteRepository;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmptyRouteServiceTest {

    @Mock
    private EmptyRouteRepository emptyRouteRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private EmptyRouteService emptyRouteService;

    @Test
    void givenCarrierId_whenListMine_thenOnlyThatCompanyRoutesAreReturned() {
        String companyId = UUID.randomUUID().toString();
        List<EmptyRoute> routes = List.of(EmptyRoute.builder().companyId(companyId).status(EmptyRouteStatus.PENDING).build());
        when(emptyRouteRepository.findByCompanyIdOrderByExpectedEmptyTimeAsc(companyId)).thenReturn(routes);

        List<EmptyRoute> result = emptyRouteService.listMine(companyId);

        assertEquals(routes, result);
        verify(emptyRouteRepository).findByCompanyIdOrderByExpectedEmptyTimeAsc(companyId);
    }

    @Test
    void givenValidRequest_whenCreateMine_thenOwnerHeaderIsPersistedAsCompanyId() {
        String ownerId = UUID.randomUUID().toString();
        UUID ownerUuid = UUID.fromString(ownerId);
        Vehicle ownedVehicle = Vehicle.builder()
            .id(UUID.randomUUID())
            .carrierId(ownerUuid)
            .licensePlate("51H-12345")
            .vehicleType(VehicleType.TRUCK_MEDIUM)
            .payloadCapacity(new BigDecimal("6.0"))
            .status(VehicleStatus.VERIFIED)
            .build();
        EmptyRouteRequest request = EmptyRouteRequest.builder()
                .truckId("51H-12345")
                .companyId("spoofed-company")
                .expectedEmptyTime(LocalDateTime.now().plusHours(2))
                .latitude(10.8)
                .longitude(106.7)
                .build();
        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-12345")).thenReturn(Optional.of(ownedVehicle));
        when(emptyRouteRepository.save(any(EmptyRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmptyRoute result = emptyRouteService.createEmptyRoute(request, ownerId);

        assertEquals(ownerId, result.getCompanyId());
        assertEquals(EmptyRouteStatus.PENDING, result.getStatus());
        verify(emptyRouteRepository).save(any(EmptyRoute.class));
    }

        @Test
        void givenVehicleNotOwnedByCarrier_whenCreateMine_thenForbidden() {
        String ownerId = UUID.randomUUID().toString();
        Vehicle foreignVehicle = Vehicle.builder()
            .id(UUID.randomUUID())
            .carrierId(UUID.randomUUID())
            .licensePlate("51H-99999")
            .vehicleType(VehicleType.TRUCK_HEAVY)
            .payloadCapacity(new BigDecimal("12.0"))
            .status(VehicleStatus.VERIFIED)
            .build();
        EmptyRouteRequest request = EmptyRouteRequest.builder()
            .truckId("51H-99999")
            .expectedEmptyTime(LocalDateTime.now().plusHours(1))
            .latitude(10.8)
            .longitude(106.7)
            .build();
        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-99999")).thenReturn(Optional.of(foreignVehicle));

        assertThrows(ResponseStatusException.class, () -> emptyRouteService.createEmptyRoute(request, ownerId));
        }

        @Test
        void givenMixedCandidates_whenFindMatchingRoutes_thenOnlySuitableVehicleIsReturned() {
        EmptyRoute suitable = EmptyRoute.builder()
            .id(UUID.randomUUID())
            .truckId("51H-11111")
            .companyId(UUID.randomUUID().toString())
            .origin("TP. Hồ Chí Minh")
            .destination("Hà Nội")
            .expectedEmptyTime(LocalDateTime.now().plusHours(1))
            .latitude(10.8)
            .longitude(106.7)
            .status(EmptyRouteStatus.PENDING)
            .build();
        EmptyRoute lowPayload = EmptyRoute.builder()
            .id(UUID.randomUUID())
            .truckId("51H-22222")
            .companyId(UUID.randomUUID().toString())
            .origin("TP. Hồ Chí Minh")
            .destination("Hà Nội")
            .expectedEmptyTime(LocalDateTime.now().plusHours(1))
            .latitude(10.8)
            .longitude(106.7)
            .status(EmptyRouteStatus.PENDING)
            .build();

        when(emptyRouteRepository.findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
            "TP. Hồ Chí Minh", "Hà Nội", EmptyRouteStatus.PENDING
        )).thenReturn(List.of(suitable, lowPayload));

        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-11111")).thenReturn(Optional.of(
            Vehicle.builder()
                .id(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .licensePlate("51H-11111")
                .vehicleType(VehicleType.TRUCK_HEAVY)
                .payloadCapacity(new BigDecimal("12.0"))
                .status(VehicleStatus.VERIFIED)
                .build()
        ));
        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-22222")).thenReturn(Optional.of(
            Vehicle.builder()
                .id(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .licensePlate("51H-22222")
                .vehicleType(VehicleType.TRUCK_HEAVY)
                .payloadCapacity(new BigDecimal("2.0"))
                .status(VehicleStatus.VERIFIED)
                .build()
        ));

        List<EmptyRoute> result = emptyRouteService.findMatchingRoutes(
            "TP. Hồ Chí Minh - Kho A",
            "Hà Nội - Kho B",
            Map.of(
                "weight", "5.0",
                "vehicleTypeRequired", "TRUCK_HEAVY"
            )
        );

        assertEquals(1, result.size());
        assertEquals("51H-11111", result.get(0).getTruckId());
    }

    @Test
    void givenPendingVehicle_whenFindMatchingRoutes_thenExcluded() {
        EmptyRoute route = EmptyRoute.builder()
            .id(UUID.randomUUID())
            .truckId("51H-33333")
            .companyId(UUID.randomUUID().toString())
            .origin("Hà Nội")
            .destination("Hải Phòng")
            .expectedEmptyTime(LocalDateTime.now().plusHours(3))
            .latitude(21.0)
            .longitude(105.8)
            .status(EmptyRouteStatus.PENDING)
            .build();

        when(emptyRouteRepository.findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
            "Hà Nội", "Hải Phòng", EmptyRouteStatus.PENDING
        )).thenReturn(List.of(route));

        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-33333")).thenReturn(Optional.of(
            Vehicle.builder()
                .id(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .licensePlate("51H-33333")
                .vehicleType(VehicleType.TRUCK_HEAVY)
                .payloadCapacity(new BigDecimal("10.0"))
                .status(VehicleStatus.PENDING)
                .build()
        ));

        List<EmptyRoute> result = emptyRouteService.findMatchingRoutes(
            "Hà Nội", "Hải Phòng",
            Map.of("weight", "5.0", "vehicleTypeRequired", "TRUCK_HEAVY")
        );

        assertEquals(0, result.size(), "PENDING vehicles must be excluded from matching");
    }

    @Test
    void givenExpiredRoute_whenFindMatchingRoutes_thenExcluded() {
        EmptyRoute expired = EmptyRoute.builder()
            .id(UUID.randomUUID())
            .truckId("51H-44444")
            .companyId(UUID.randomUUID().toString())
            .origin("Đà Nẵng")
            .destination("Huế")
            .expectedEmptyTime(LocalDateTime.now().minusDays(3))
            .latitude(16.0)
            .longitude(108.2)
            .status(EmptyRouteStatus.PENDING)
            .build();

        when(emptyRouteRepository.findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
            "Đà Nẵng", "Huế", EmptyRouteStatus.PENDING
        )).thenReturn(List.of(expired));

        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-44444")).thenReturn(Optional.of(
            Vehicle.builder()
                .id(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .licensePlate("51H-44444")
                .vehicleType(VehicleType.TRUCK_MEDIUM)
                .payloadCapacity(new BigDecimal("8.0"))
                .status(VehicleStatus.VERIFIED)
                .build()
        ));

        List<EmptyRoute> result = emptyRouteService.findMatchingRoutes(
            "Đà Nẵng", "Huế",
            Map.of("weight", "5.0", "vehicleTypeRequired", "TRUCK_MEDIUM")
        );

        assertEquals(0, result.size(), "Expired routes must be excluded from matching");
    }

    @Test
    void givenUnverifiedVehicle_whenCreateEmptyRoute_thenConflict() {
        String ownerId = UUID.randomUUID().toString();
        UUID ownerUuid = UUID.fromString(ownerId);
        Vehicle pendingVehicle = Vehicle.builder()
            .id(UUID.randomUUID())
            .carrierId(ownerUuid)
            .licensePlate("51H-55555")
            .vehicleType(VehicleType.TRUCK_MEDIUM)
            .payloadCapacity(new BigDecimal("6.0"))
            .status(VehicleStatus.PENDING)
            .build();
        EmptyRouteRequest request = EmptyRouteRequest.builder()
            .truckId("51H-55555")
            .expectedEmptyTime(LocalDateTime.now().plusHours(2))
            .latitude(10.8)
            .longitude(106.7)
            .origin("TP. Hồ Chí Minh")
            .destination("Hà Nội")
            .build();
        when(vehicleRepository.findByLicensePlateIgnoreCase("51H-55555")).thenReturn(Optional.of(pendingVehicle));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> emptyRouteService.createEmptyRoute(request, ownerId)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }
}
