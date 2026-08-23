package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.UpdateTripStatusRequest;
import iuh.fit.se.contractservice.dto.AssignDriverRequest;
import iuh.fit.se.contractservice.dto.CreateJourneyEventRequest;
import iuh.fit.se.contractservice.dto.CreateDeliveryProofRequest;
import iuh.fit.se.contractservice.dto.CreateTripLocationRequest;
import iuh.fit.se.contractservice.domain.entity.DeliveryProof;
import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import iuh.fit.se.contractservice.repository.JourneyEventRepository;
import iuh.fit.se.contractservice.repository.TrackingLogRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.DeliveryProofRepository;
import iuh.fit.se.contractservice.repository.TripLocationUpdateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {
    @Mock
    private TripRepository tripRepository;

    @Mock
    private TrackingLogRepository trackingLogRepository;

    @Mock
    private JourneyEventRepository journeyEventRepository;

    @Mock
    private DeliveryProofRepository deliveryProofRepository;

    @Mock
    private TripLocationUpdateRepository tripLocationUpdateRepository;

    private TripService tripService;
    private Trip trip;
    private UUID carrierId;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, trackingLogRepository, journeyEventRepository, deliveryProofRepository, tripLocationUpdateRepository);
        carrierId = UUID.randomUUID();
        trip = Trip.builder()
                .id(UUID.randomUUID())
                .carrierId(carrierId)
                .status(TripStatus.WAITING_PICKUP)
                .build();
        lenient().when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
    }

    @Test
    void carrierCanProgressTripThroughLegalStatusTransition() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trip result = tripService.updateStatus(
                carrierId,
                AccountRole.CARRIER,
                trip.getId(),
                new UpdateTripStatusRequest(TripStatus.PICKED_UP, null));

        assertThat(result.getStatus()).isEqualTo(TripStatus.PICKED_UP);
    }

    @Test
    void assignmentGeneratesAOneTimeSixDigitPinAndStoresOnlyItsHash() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID driverId = UUID.randomUUID();

        var result = tripService.assignDriver(
                carrierId,
                AccountRole.CARRIER,
                trip.getId(),
                new AssignDriverRequest(driverId));

        assertThat(result.driverId()).isEqualTo(driverId);
        assertThat(result.assignmentPin()).matches("\\d{6}");
        assertThat(trip.getAssignmentPinHash()).hasSize(64).doesNotContain(result.assignmentPin());
    }

    @Test
    void illegalTransitionIsRejected() {
        assertThatThrownBy(() -> tripService.updateStatus(
                carrierId,
                AccountRole.CARRIER,
                trip.getId(),
                new UpdateTripStatusRequest(TripStatus.DELIVERED, null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Invalid trip status transition");
    }

    @Test
    void carrierCanRecordManualJourneyMilestoneWithoutCoordinates() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyEventRepository.save(any(JourneyEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JourneyEvent result = tripService.addJourneyEvent(
                carrierId,
                AccountRole.CARRIER,
                trip.getId(),
                new CreateJourneyEventRequest(JourneyEventType.ARRIVED_PICKUP, null, "Đã tới điểm lấy hàng", null));

        assertThat(result.getEventType()).isEqualTo(JourneyEventType.ARRIVED_PICKUP);
        assertThat(result.getTrip().getStatus()).isEqualTo(TripStatus.WAITING_PICKUP);
        assertThat(result.getNote()).isEqualTo("Đã tới điểm lấy hàng");
    }

    @Test
    void manualJourneyEventRequiresLegalStatusTransition() {
        assertThatThrownBy(() -> tripService.addJourneyEvent(
                carrierId,
                AccountRole.CARRIER,
                trip.getId(),
                new CreateJourneyEventRequest(JourneyEventType.DELIVERY_ACCEPTED, TripStatus.COMPLETED, "x", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Invalid trip status transition");
    }

    @Test
    void assignedDriverCanRecordManualJourneyEvent() {
        UUID driverId = UUID.randomUUID();
        trip.setDriverId(driverId);
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyEventRepository.save(any(JourneyEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JourneyEvent result = tripService.addJourneyEvent(
                driverId,
                AccountRole.DRIVER,
                trip.getId(),
                new CreateJourneyEventRequest(JourneyEventType.ARRIVED_PICKUP, null, "Đã tới nơi", null));

        assertThat(result.getActorId()).isEqualTo(driverId);
    }

    @Test
    void unrelatedDriverCannotReadTripJourney() {
        trip.setDriverId(UUID.randomUUID());
        assertThatThrownBy(() -> tripService.journey(
                UUID.randomUUID(), AccountRole.DRIVER, trip.getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    void driverCannotCompleteTrip() {
        UUID driverId = UUID.randomUUID();
        trip.setDriverId(driverId);
        trip.setStatus(TripStatus.DELIVERED);

        assertThatThrownBy(() -> tripService.addJourneyEvent(
                driverId,
                AccountRole.DRIVER,
                trip.getId(),
                new CreateJourneyEventRequest(JourneyEventType.DELIVERY_ACCEPTED, TripStatus.COMPLETED, null, null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Driver cannot complete");
    }

    @Test
    void shipperCanAcceptDeliveredProofAndCompleteTrip() {
        UUID shipperId = UUID.randomUUID();
        trip.setShipperId(shipperId);
        trip.setStatus(TripStatus.DELIVERED);
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(journeyEventRepository.save(any(JourneyEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JourneyEvent result = tripService.addJourneyEvent(
                shipperId,
                AccountRole.SHIPPER,
                trip.getId(),
                new CreateJourneyEventRequest(JourneyEventType.DELIVERY_ACCEPTED, TripStatus.COMPLETED, "Đã nhận đủ hàng", null));

        assertThat(result.getStatus()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void assignedDriverCanSubmitDeliveryProof() {
        UUID driverId = UUID.randomUUID();
        trip.setDriverId(driverId);
        when(deliveryProofRepository.save(any(DeliveryProof.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryProof result = tripService.addDeliveryProof(
                driverId, AccountRole.DRIVER, trip.getId(),
                new CreateDeliveryProofRequest("https://sandbox.local/proof.jpg", "Giao đủ kiện"));

        assertThat(result.getImageUrl()).isEqualTo("https://sandbox.local/proof.jpg");
        assertThat(result.getTrip()).isSameAs(trip);
    }

    @Test
    void shipperCannotSubmitDeliveryProof() {
        assertThatThrownBy(() -> tripService.addDeliveryProof(
                UUID.randomUUID(), AccountRole.SHIPPER, trip.getId(),
                new CreateDeliveryProofRequest("https://sandbox.local/proof.jpg", null)))
                .hasMessageContaining("Carrier, driver or admin role required");
    }

    @Test
    void assignedDriverCanSubmitManualLocation() {
        UUID driverId = UUID.randomUUID();
        trip.setDriverId(driverId);
        when(tripLocationUpdateRepository.save(any(TripLocationUpdate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripLocationUpdate result = tripService.addLocation(
                driverId, AccountRole.DRIVER, trip.getId(),
                new CreateTripLocationRequest(10.7769, 106.7009, "Đã tới điểm trung chuyển"));

        assertThat(result.getLatitude()).isEqualTo(10.7769);
        assertThat(result.getLongitude()).isEqualTo(106.7009);
        assertThat(result.getLabel()).isEqualTo("Đã tới điểm trung chuyển");
    }
}
