package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.TrackingLog;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CreateTrackingRequest;
import iuh.fit.se.contractservice.dto.AssignDriverRequest;
import iuh.fit.se.contractservice.dto.TripAssignmentResponse;
import iuh.fit.se.contractservice.dto.UpdateTripStatusRequest;
import iuh.fit.se.contractservice.dto.CreateJourneyEventRequest;
import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import iuh.fit.se.contractservice.domain.entity.DeliveryProof;
import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import iuh.fit.se.contractservice.domain.entity.TripLocationSource;
import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.repository.JourneyEventRepository;
import iuh.fit.se.contractservice.repository.DeliveryProofRepository;
import iuh.fit.se.contractservice.repository.TripLocationUpdateRepository;
import iuh.fit.se.contractservice.repository.TrackingLogRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.TripDelaySettlementRepository;
import iuh.fit.se.contractservice.repository.TripTrackingSessionRepository;
import iuh.fit.se.contractservice.dto.TripDelaySettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import iuh.fit.se.contractservice.service.driveraccess.DriverAccessPolicy;
import iuh.fit.se.contractservice.service.driveraccess.DriverGrantService;
import iuh.fit.se.contractservice.service.driveraccess.DriverScope;
import iuh.fit.se.contractservice.dto.CreateDeliveryProofRequest;
import iuh.fit.se.contractservice.dto.CreateTripLocationRequest;

@Service
@RequiredArgsConstructor
public class TripService {
    private final TripRepository tripRepository;
    private final TrackingLogRepository trackingLogRepository;
    private final JourneyEventRepository journeyEventRepository;
    private final DeliveryProofRepository deliveryProofRepository;
    private final TripLocationUpdateRepository tripLocationUpdateRepository;
    private final TripDelaySettlementRepository tripDelaySettlementRepository;
    private final LateDeliveryService lateDeliveryService;
    private final FleetDriverClient fleetDriverClient;
    private final TripTrackingSessionRepository trackingSessionRepository;
    private final DriverGrantService driverGrantService;
    private final DriverAccessPolicy driverAccessPolicy;
    private final TripHandoverService tripHandoverService;

    @Transactional(readOnly = true)
    public List<Trip> list(UUID accountId, AccountRole role, TripStatus status) {
        List<Trip> trips = switch (role) {
            case ADMIN -> tripRepository.findAllByOrderByCreatedAtDesc();
            case CARRIER -> tripRepository.findByCarrierIdOrderByCreatedAtDesc(accountId);
            // A code-login session sees exactly its own trip; a legacy driver account sees its claimed trips.
            case DRIVER -> DriverAccessPolicy.isDriverSession() ? scopedTrip(accountId, role)
                    : tripRepository.findByDriverAccountIdOrderByCreatedAtDesc(accountId);
            case SHIPPER -> tripRepository.findByShipperIdOrderByCreatedAtDesc(accountId);
        };
        return status == null ? trips : trips.stream().filter(trip -> trip.getStatus() == status).toList();
    }

    @Transactional(readOnly = true)
    public Trip get(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        ensureAccess(trip, accountId, role);
        return trip;
    }

    @Transactional
    public Trip updateStatus(UUID accountId, AccountRole role, UUID tripId, UpdateTripStatusRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
        Trip trip = lock(accountId, role, tripId);
        if (!isAllowedTransition(trip.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid trip status transition from " + trip.getStatus() + " to " + request.status());
        }
        if (request.status() == TripStatus.CANCELLED
                && (request.cancellationReason() == null || request.cancellationReason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        }
        if (request.status() == TripStatus.PICKED_UP) {
            tripHandoverService.requireReadyForPickup(trip.getId());
        }
        trip.updateStatus(request.status());
        stopTrackingIfClosed(trip);
        trip.setCancellationReason(request.status() == TripStatus.CANCELLED ? request.cancellationReason().trim() : null);
        Trip saved = tripRepository.save(trip);
        if (request.status() == TripStatus.DELIVERED) lateDeliveryService.processTrip(saved);
        if (request.status() == TripStatus.CANCELLED || request.status() == TripStatus.COMPLETED) {
            lateDeliveryService.processTripAndReleaseIfClosed(saved);
        }
        return saved;
    }

    @Transactional
    public TripAssignmentResponse assignDriver(
            UUID accountId,
            AccountRole role,
            UUID tripId,
            AssignDriverRequest request
    ) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        ensureAccess(trip, accountId, role);
        if (trip.getStatus() != TripStatus.WAITING_PICKUP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only waiting trips can be assigned");
        }
        fleetDriverClient.requireAssignableDriver(trip.getCarrierId(), request.driverId());
        var issued = driverGrantService.issue(trip, request.driverId(), accountId, "REASSIGNED");
        return new TripAssignmentResponse(trip.getId(), trip.getDriverId(), issued.grantId(), issued.code(),
                issued.expiresAt(), trip.getStatus(), issued.assignmentVersion());
    }

    /**
     * New code for the same fleet profile on an active trip (lost phone or session). Status, events and GPS
     * history stay; the previous code, its session and any legacy driver account lose access.
     */
    @Transactional
    public TripAssignmentResponse reissueDriverAccess(UUID accountId, AccountRole role, UUID tripId) {
        if (role != AccountRole.CARRIER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier role required");
        }
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        ensureAccess(trip, accountId, role);
        if (trip.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chuyến chưa được phân công tài xế");
        }
        if (trip.getStatus() != TripStatus.WAITING_PICKUP && trip.getStatus() != TripStatus.PICKED_UP
                && trip.getStatus() != TripStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ cấp lại mã cho chuyến đang hoạt động");
        }
        fleetDriverClient.requireAssignableDriver(trip.getCarrierId(), trip.getDriverId());
        var issued = driverGrantService.issue(trip, trip.getDriverId(), accountId, "REISSUED");
        return new TripAssignmentResponse(trip.getId(), trip.getDriverId(), issued.grantId(), issued.code(),
                issued.expiresAt(), trip.getStatus(), issued.assignmentVersion());
    }

    private List<Trip> scopedTrip(UUID sessionId, AccountRole role) {
        UUID scoped = DriverScope.current().tripId();
        if (scoped == null) return List.of();
        return tripRepository.findById(scoped)
                .filter(trip -> driverAccessPolicy.allows(trip, sessionId, role, DriverAccessPolicy.Mode.READ))
                .map(List::of).orElse(List.of());
    }

    @Transactional
    public TrackingLog addTracking(UUID accountId, AccountRole role, UUID tripId, CreateTrackingRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
        Trip trip = lock(accountId, role, tripId);
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add tracking to a closed trip");
        }
        if (trip.getStatus() != request.status() && !isAllowedTransition(trip.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid trip status transition from " + trip.getStatus() + " to " + request.status());
        }
        trip.updateStatus(request.status());
        stopTrackingIfClosed(trip);
        Trip saved = tripRepository.save(trip);
        if (request.status() == TripStatus.DELIVERED) lateDeliveryService.processTrip(saved);
        if (request.status() == TripStatus.CANCELLED || request.status() == TripStatus.COMPLETED) {
            lateDeliveryService.processTripAndReleaseIfClosed(saved);
        }
        TrackingLog log = TrackingLog.builder()
                .trip(saved)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .status(request.status())
                .build();
        return trackingLogRepository.save(log);
    }

    @Transactional
    public JourneyEvent addJourneyEvent(UUID accountId, AccountRole role, UUID tripId, CreateJourneyEventRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN && role != AccountRole.DRIVER
                && role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role for journey updates");
        }
        Trip trip = lock(accountId, role, tripId);
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot update a closed trip");
        }
        TripStatus nextStatus = request.status() == null ? trip.getStatus() : request.status();
        if (role == AccountRole.DRIVER
                && (nextStatus == TripStatus.COMPLETED || nextStatus == TripStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver cannot complete or cancel a trip");
        }
        if (nextStatus != trip.getStatus() && !isAllowedTransition(trip.getStatus(), nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid trip status transition from " + trip.getStatus() + " to " + nextStatus);
        }
        if (nextStatus == TripStatus.PICKED_UP && trip.getStatus() != TripStatus.PICKED_UP) {
            tripHandoverService.requireReadyForPickup(trip.getId());
        }
        if (request.eventType() == JourneyEventType.ADMIN_OVERRIDE && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for override");
        }
        if (role == AccountRole.SHIPPER
                && (request.eventType() != JourneyEventType.DELIVERY_ACCEPTED || nextStatus != TripStatus.COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shipper can only accept delivered proof");
        }
        if (request.eventType() == JourneyEventType.INCIDENT_REPORTED
                && (request.note() == null || request.note().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incident note is required");
        }
        trip.updateStatus(nextStatus);
        stopTrackingIfClosed(trip);
        tripRepository.save(trip);
        if (nextStatus == TripStatus.DELIVERED) lateDeliveryService.processTrip(trip);
        if (nextStatus == TripStatus.CANCELLED || nextStatus == TripStatus.COMPLETED) {
            lateDeliveryService.processTripAndReleaseIfClosed(trip);
        }
        return journeyEventRepository.save(JourneyEvent.builder()
                .trip(trip)
                .actorId(accountId)
                .eventType(request.eventType())
                .status(nextStatus)
                .note(request.note() == null ? null : request.note().trim())
                .evidenceUrl(request.evidenceUrl())
                .build());
    }

    @Transactional(readOnly = true)
    public List<JourneyEvent> journey(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = get(accountId, role, tripId);
        return journeyEventRepository.findByTripIdOrderByRecordedAtAsc(trip.getId());
    }

    @Transactional
    public DeliveryProof addDeliveryProof(UUID accountId, AccountRole role, UUID tripId,
                                          CreateDeliveryProofRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN && role != AccountRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier, driver or admin role required");
        }
        Trip trip = lock(accountId, role, tripId);
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add proof to a closed trip");
        }
        return deliveryProofRepository.save(DeliveryProof.builder()
                .trip(trip)
                .imageUrl(request.imageUrl().trim())
                .note(request.note() == null ? null : request.note().trim())
                .build());
    }

    @Transactional(readOnly = true)
    public List<DeliveryProof> proofs(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = get(accountId, role, tripId);
        return deliveryProofRepository.findByTripIdOrderByUploadedAtAsc(trip.getId());
    }

    @Transactional
    public TripLocationUpdate addLocation(UUID accountId, AccountRole role, UUID tripId,
                                          CreateTripLocationRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN && role != AccountRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier, driver or admin role required");
        }
        Trip trip = lock(accountId, role, tripId);
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot update location for a closed trip");
        }
        return tripLocationUpdateRepository.save(TripLocationUpdate.builder()
                .trip(trip)
                .actorId(accountId)
                .actorType(role == AccountRole.DRIVER && DriverAccessPolicy.isDriverSession() ? "DRIVER_SESSION" : "ACCOUNT")
                .actorRole(role)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .label(request.label() == null ? null : request.label().trim())
                .source(TripLocationSource.MANUAL)
                .status(trip.getStatus())
                .build());
    }

    @Transactional(readOnly = true)
    public List<TripLocationUpdate> locations(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = get(accountId, role, tripId);
        return tripLocationUpdateRepository.findByTripIdOrderByRecordedAtAsc(trip.getId());
    }

    @Transactional(readOnly = true)
    public TripLocationUpdate latestLocation(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = get(accountId, role, tripId);
        return tripLocationUpdateRepository.findFirstByTripIdOrderByRecordedAtDesc(trip.getId());
    }

    @Transactional
    public Trip cancelForLateDelivery(UUID accountId, AccountRole role, UUID tripId, String reason) {
        if (role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shipper can cancel a late trip");
        }
        Trip trip = lock(accountId, role, tripId);
        boolean active = trip.getStatus() == TripStatus.WAITING_PICKUP
                || trip.getStatus() == TripStatus.PICKED_UP || trip.getStatus() == TripStatus.IN_TRANSIT;
        if (!active || trip.getDeliveredAt() != null || trip.getExpectedDeliveryAt() == null
                || !lateDeliveryService.isLatePolicyEnabled(trip)
                || !trip.getExpectedDeliveryAt().plus(java.time.Duration.ofHours(1)).isBefore(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ được hủy chuyến đang hoạt động khi đã trễ hơn 1 giờ và có tiền đặt trước được giữ");
        }
        lateDeliveryService.processTrip(trip);
        trip.cancel(reason == null || reason.isBlank() ? "Chủ hàng hủy do giao trễ hơn 1 giờ" : reason.trim());
        stopTrackingIfClosed(trip);
        Trip cancelled = tripRepository.save(trip);
        lateDeliveryService.releaseRemainingDepositIfClosed(cancelled);
        lateDeliveryService.notifyLateCancellation(cancelled);
        return cancelled;
    }

    @Transactional(readOnly = true)
    public List<TripDelaySettlementResponse> delaySettlements(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = get(accountId, role, tripId);
        return tripDelaySettlementRepository.findByTripIdOrderByTierAsc(trip.getId()).stream()
                .map(TripDelaySettlementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TripDelaySettlementResponse> allDelaySettlementsForAdmin() {
        return lateDeliveryService.listForAdmin().stream().map(TripDelaySettlementResponse::from).toList();
    }

    private Trip lock(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        ensureAccess(trip, accountId, role, DriverAccessPolicy.Mode.WRITE);
        return trip;
    }

    private void stopTrackingIfClosed(Trip trip) {
        if (trip.getStatus() == TripStatus.DELIVERED || trip.getStatus() == TripStatus.COMPLETED
                || trip.getStatus() == TripStatus.CANCELLED) {
            trackingSessionRepository.stopForTrip(trip.getId(), java.time.Instant.now());
        }
    }

    private void ensureAccess(Trip trip, UUID accountId, AccountRole role) {
        ensureAccess(trip, accountId, role, DriverAccessPolicy.Mode.READ);
    }

    private void ensureAccess(Trip trip, UUID accountId, AccountRole role, DriverAccessPolicy.Mode mode) {
        boolean allowed = role == AccountRole.ADMIN
                || (role == AccountRole.CARRIER && accountId.equals(trip.getCarrierId()))
                || driverAccessPolicy.allows(trip, accountId, role, mode)
                || (role == AccountRole.SHIPPER && accountId.equals(trip.getShipperId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }
    }

    private boolean isAllowedTransition(TripStatus current, TripStatus next) {
        return switch (current) {
            case WAITING_PICKUP -> next == TripStatus.PICKED_UP || next == TripStatus.CANCELLED;
            case PICKED_UP -> next == TripStatus.IN_TRANSIT || next == TripStatus.CANCELLED;
            case IN_TRANSIT -> next == TripStatus.DELIVERED || next == TripStatus.CANCELLED;
            case DELIVERED -> next == TripStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
