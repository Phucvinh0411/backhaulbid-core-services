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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
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
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<Trip> list(UUID accountId, AccountRole role, TripStatus status) {
        List<Trip> trips = switch (role) {
            case ADMIN -> tripRepository.findAllByOrderByCreatedAtDesc();
            case CARRIER -> tripRepository.findByCarrierIdOrderByCreatedAtDesc(accountId);
            case DRIVER -> tripRepository.findByDriverIdOrderByCreatedAtDesc(accountId);
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
        Trip trip = get(accountId, role, tripId);
        if (!isAllowedTransition(trip.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid trip status transition from " + trip.getStatus() + " to " + request.status());
        }
        if (request.status() == TripStatus.CANCELLED
                && (request.cancellationReason() == null || request.cancellationReason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        }
        trip.setStatus(request.status());
        trip.setCancellationReason(request.status() == TripStatus.CANCELLED ? request.cancellationReason().trim() : null);
        return tripRepository.save(trip);
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
        Trip trip = get(accountId, role, tripId);
        if (trip.getStatus() != TripStatus.WAITING_PICKUP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only waiting trips can be assigned");
        }
        String pin = String.format("%06d", secureRandom.nextInt(1_000_000));
        trip.setDriverId(request.driverId());
        trip.setAssignmentPinHash(sha256(pin));
        tripRepository.save(trip);
        return new TripAssignmentResponse(trip.getId(), trip.getDriverId(), pin, trip.getStatus());
    }

    @Transactional
    public TrackingLog addTracking(UUID accountId, AccountRole role, UUID tripId, CreateTrackingRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
        Trip trip = get(accountId, role, tripId);
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add tracking to a closed trip");
        }
        if (trip.getStatus() != request.status() && !isAllowedTransition(trip.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid trip status transition from " + trip.getStatus() + " to " + request.status());
        }
        trip.setStatus(request.status());
        tripRepository.save(trip);
        TrackingLog log = TrackingLog.builder()
                .trip(trip)
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
        Trip trip = get(accountId, role, tripId);
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
        trip.setStatus(nextStatus);
        tripRepository.save(trip);
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
        Trip trip = get(accountId, role, tripId);
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
        Trip trip = get(accountId, role, tripId);
        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot update location for a closed trip");
        }
        return tripLocationUpdateRepository.save(TripLocationUpdate.builder()
                .trip(trip)
                .actorId(accountId)
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

    private void ensureAccess(Trip trip, UUID accountId, AccountRole role) {
        boolean allowed = role == AccountRole.ADMIN
                || (role == AccountRole.CARRIER && accountId.equals(trip.getCarrierId()))
                || (role == AccountRole.DRIVER && accountId.equals(trip.getDriverId()))
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
