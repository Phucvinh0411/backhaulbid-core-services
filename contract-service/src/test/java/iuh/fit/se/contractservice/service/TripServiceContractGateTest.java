package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.AssignDriverRequest;
import iuh.fit.se.contractservice.dto.TripAssignmentResponse;
import iuh.fit.se.contractservice.mapper.TripMapper;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.DeliveryProofRepository;
import iuh.fit.se.contractservice.repository.JourneyEventRepository;
import iuh.fit.se.contractservice.repository.TrackingLogRepository;
import iuh.fit.se.contractservice.repository.TripDelaySettlementRepository;
import iuh.fit.se.contractservice.repository.TripLocationUpdateRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.TripTrackingSessionRepository;
import iuh.fit.se.contractservice.service.driveraccess.DriverAccessPolicy;
import iuh.fit.se.contractservice.service.driveraccess.DriverGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceContractGateTest {

    @Mock private TripRepository tripRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private TrackingLogRepository trackingLogRepository;
    @Mock private JourneyEventRepository journeyEventRepository;
    @Mock private DeliveryProofRepository deliveryProofRepository;
    @Mock private TripLocationUpdateRepository tripLocationUpdateRepository;
    @Mock private TripDelaySettlementRepository tripDelaySettlementRepository;
    @Mock private LateDeliveryService lateDeliveryService;
    @Mock private FleetDriverClient fleetDriverClient;
    @Mock private TripTrackingSessionRepository trackingSessionRepository;
    @Mock private DriverGrantService driverGrantService;
    @Mock private DriverAccessPolicy driverAccessPolicy;
    @Mock private TripHandoverService tripHandoverService;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(
                tripRepository,
                contractRepository,
                trackingLogRepository,
                journeyEventRepository,
                deliveryProofRepository,
                tripLocationUpdateRepository,
                tripDelaySettlementRepository,
                lateDeliveryService,
                fleetDriverClient,
                trackingSessionRepository,
                driverGrantService,
                driverAccessPolicy,
                tripHandoverService,
                Mappers.getMapper(TripMapper.class));
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"DRAFT", "WAITING_SIGNATURE"})
    void assignDriverRejectsUnsignedContractWithConflict(ContractStatus unsignedStatus) {
        UUID tripId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Trip trip = Trip.builder()
                .id(tripId)
                .carrierId(carrierId)
                .shipperId(UUID.randomUUID())
                .status(TripStatus.WAITING_PICKUP)
                .build();
        Contract contract = Contract.builder()
                .id(UUID.randomUUID())
                .trip(trip)
                .status(unsignedStatus)
                .build();

        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(contractRepository.findByTripId(tripId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> tripService.assignDriver(
                carrierId, AccountRole.CARRIER, tripId, new AssignDriverRequest(driverId)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo(TripService.UNSIGNED_CONTRACT_MESSAGE);
                });

        verifyNoInteractions(fleetDriverClient, driverGrantService);
    }

    @Test
    void assignDriverSucceedsWhenContractIsSigned() {
        UUID tripId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-18T12:00:00Z");
        Trip trip = Trip.builder()
                .id(tripId)
                .carrierId(carrierId)
                .shipperId(UUID.randomUUID())
                .status(TripStatus.WAITING_PICKUP)
                .build();
        Contract contract = Contract.builder()
                .id(UUID.randomUUID())
                .trip(trip)
                .status(ContractStatus.SIGNED)
                .build();

        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(contractRepository.findByTripId(tripId)).thenReturn(Optional.of(contract));
        when(driverGrantService.issue(trip, driverId, carrierId, "REASSIGNED"))
                .thenAnswer(inv -> {
                    trip.setDriverId(driverId);
                    return new DriverGrantService.Issued(grantId, "12345678", expiresAt, 1L);
                });

        TripAssignmentResponse response = tripService.assignDriver(
                carrierId, AccountRole.CARRIER, tripId, new AssignDriverRequest(driverId));

        verify(fleetDriverClient).requireAssignableDriver(carrierId, driverId);
        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.driverProfileId()).isEqualTo(driverId);
        assertThat(response.grantId()).isEqualTo(grantId);
        assertThat(response.code()).isEqualTo("12345678");
    }

    @Test
    void sanitizeIpAndNotificationUrlNormalizeCleanly() {
        assertThat(ContractService.sanitizeIp(" 203.0.113.195 , 70.41.3.18 ")).isEqualTo("203.0.113.195");
        assertThat(ContractService.sanitizeIp("x".repeat(60))).hasSize(45);
        assertThat(ContractService.sanitizeIp("  , ")).isNull();
        assertThat(LateDeliveryNotificationClient.resolveInternalCreateUrl("http://notification-service:3002"))
                .isEqualTo("http://notification-service:3002/api/v1/notifications/internal/create");
        assertThat(LateDeliveryNotificationClient.resolveInternalCreateUrl("http://notification-service:3002/api/v1/notifications/"))
                .isEqualTo("http://notification-service:3002/api/v1/notifications/internal/create");
    }
}
