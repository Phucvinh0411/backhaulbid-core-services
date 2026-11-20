package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CreateJourneyEventRequest;
import iuh.fit.se.contractservice.dto.CreateTrackingRequest;
import iuh.fit.se.contractservice.dto.UpdateTripStatusRequest;
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
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceCompletionPolicyTest {

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

    private UUID tripId;
    private UUID carrierId;
    private UUID shipperId;
    private Trip deliveredTrip;

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

        tripId = UUID.randomUUID();
        carrierId = UUID.randomUUID();
        shipperId = UUID.randomUUID();
        deliveredTrip = Trip.builder()
                .id(tripId)
                .carrierId(carrierId)
                .shipperId(shipperId)
                .status(TripStatus.DELIVERED)
                .build();
        Contract signedContract = Contract.builder()
                .id(UUID.randomUUID())
                .trip(deliveredTrip)
                .status(ContractStatus.SIGNED)
                .build();

        lenient().when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(deliveredTrip));
        lenient().when(contractRepository.findByTripId(tripId)).thenReturn(Optional.of(signedContract));
    }

    @Test
    void carrierCannotCompleteTripViaUpdateStatus() {
        assertThatThrownBy(() -> tripService.updateStatus(
                carrierId,
                AccountRole.CARRIER,
                tripId,
                new UpdateTripStatusRequest(TripStatus.COMPLETED, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(deliveredTrip.getStatus()).isEqualTo(TripStatus.DELIVERED);
        verify(tripRepository, never()).save(any());
        verifyNoInteractions(lateDeliveryService);
    }

    @Test
    void carrierCannotCompleteTripViaAddTracking() {
        assertThatThrownBy(() -> tripService.addTracking(
                carrierId,
                AccountRole.CARRIER,
                tripId,
                new CreateTrackingRequest(10.7626, 106.6601, TripStatus.COMPLETED)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(deliveredTrip.getStatus()).isEqualTo(TripStatus.DELIVERED);
        verify(tripRepository, never()).save(any());
        verifyNoInteractions(trackingLogRepository, lateDeliveryService);
    }

    @Test
    void carrierCannotCompleteTripViaAddJourneyEvent() {
        assertThatThrownBy(() -> tripService.addJourneyEvent(
                carrierId,
                AccountRole.CARRIER,
                tripId,
                new CreateJourneyEventRequest(
                        JourneyEventType.ARRIVED_DELIVERY,
                        TripStatus.COMPLETED,
                        null,
                        null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(deliveredTrip.getStatus()).isEqualTo(TripStatus.DELIVERED);
        verify(tripRepository, never()).save(any());
        verifyNoInteractions(journeyEventRepository, lateDeliveryService);
    }

    @Test
    void carrierCannotSubmitDeliveryAcceptedEvent() {
        assertThatThrownBy(() -> tripService.addJourneyEvent(
                carrierId,
                AccountRole.CARRIER,
                tripId,
                new CreateJourneyEventRequest(
                        JourneyEventType.DELIVERY_ACCEPTED,
                        null,
                        null,
                        null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(tripRepository, never()).save(any());
        verifyNoInteractions(journeyEventRepository, lateDeliveryService);
    }

    @Test
    void shipperCanCompleteDeliveredTripWithDeliveryAcceptedEvent() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(journeyEventRepository.save(any(JourneyEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        JourneyEvent event = tripService.addJourneyEvent(
                shipperId,
                AccountRole.SHIPPER,
                tripId,
                new CreateJourneyEventRequest(
                        JourneyEventType.DELIVERY_ACCEPTED,
                        TripStatus.COMPLETED,
                        "Đã nhận đủ hàng",
                        null));

        assertThat(deliveredTrip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(event.getEventType()).isEqualTo(JourneyEventType.DELIVERY_ACCEPTED);
        assertThat(event.getStatus()).isEqualTo(TripStatus.COMPLETED);
        verify(lateDeliveryService).processTripAndReleaseIfClosed(deliveredTrip);
    }

    @Test
    void adminCanStillCompleteDeliveredTrip() {
        UUID adminId = UUID.randomUUID();
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip updated = tripService.updateStatus(
                adminId,
                AccountRole.ADMIN,
                tripId,
                new UpdateTripStatusRequest(TripStatus.COMPLETED, null));

        assertThat(updated.getStatus()).isEqualTo(TripStatus.COMPLETED);
        verify(lateDeliveryService).processTripAndReleaseIfClosed(deliveredTrip);
    }
}
