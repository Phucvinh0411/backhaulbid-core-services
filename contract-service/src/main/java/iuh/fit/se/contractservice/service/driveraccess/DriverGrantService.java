package iuh.fit.se.contractservice.service.driveraccess;

import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripDriverGrant;
import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.repository.DeliveryProofRepository;
import iuh.fit.se.contractservice.repository.JourneyEventRepository;
import iuh.fit.se.contractservice.repository.TripDriverGrantRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.TripTrackingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues, redeems and revokes driver assignment codes. Lock order is always trip row, then grant row.
 */
@Service
public class DriverGrantService {
    public static final Duration CODE_TTL = Duration.ofHours(24);
    /** A retry with the same code and request ID inside this window gets the same session back. */
    public static final Duration REDEEM_REPLAY_WINDOW = Duration.ofMinutes(2);
    private static final int ID_ATTEMPTS = 5;

    public record Issued(UUID grantId, String code, Instant expiresAt, long assignmentVersion) {
        @Override public String toString() { return "Issued(" + grantId + ", <code redacted>)"; }
    }

    public record Exchange(UUID sessionId, UUID tripId, UUID driverProfileId, UUID grantId, long assignmentVersion,
                           UUID carrierId, boolean replay) {
    }

    public record SessionStatus(UUID sessionId, UUID tripId, UUID driverProfileId, UUID grantId, long assignmentVersion,
                                boolean readable, boolean writable, String tripStatus, Instant readUntil) {
    }

    private final TripDriverGrantRepository grants;
    private final TripRepository trips;
    private final TripTrackingSessionRepository trackingSessions;
    private final JourneyEventRepository events;
    private final DeliveryProofRepository proofs;
    private final Clock clock;
    private final UuidV7 ids;
    private final SecureRandom random;

    @Autowired
    public DriverGrantService(TripDriverGrantRepository grants, TripRepository trips, TripTrackingSessionRepository trackingSessions,
                              JourneyEventRepository events, DeliveryProofRepository proofs, Clock clock) {
        this(grants, trips, trackingSessions, events, proofs, clock, new UuidV7(clock, new SecureRandom()), new SecureRandom());
    }

    DriverGrantService(TripDriverGrantRepository grants, TripRepository trips, TripTrackingSessionRepository trackingSessions,
                       JourneyEventRepository events, DeliveryProofRepository proofs, Clock clock, UuidV7 ids, SecureRandom random) {
        this.grants = grants;
        this.trips = trips;
        this.trackingSessions = trackingSessions;
        this.events = events;
        this.proofs = proofs;
        this.clock = clock;
        this.ids = ids;
        this.random = random;
    }

    /**
     * Revokes every earlier grant/session of the trip, bumps the assignment version and issues a new code
     * for {@code profileId}. The caller holds the trip row lock and has checked ownership and fleet membership.
     * Status, events and GPS history are untouched. The plaintext code is returned once and never stored.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issue(Trip lockedTrip, UUID profileId, UUID issuedBy, String reason) {
        Instant now = clock.instant();
        grants.revokeAllForTrip(lockedTrip.getId(), now, reason);
        trackingSessions.stopForTrip(lockedTrip.getId(), now);
        lockedTrip.setAssignmentVersion(lockedTrip.getAssignmentVersion() + 1);
        lockedTrip.setDriverId(profileId);
        lockedTrip.setDriverSessionId(null);
        // A legacy driver account loses access as soon as a new code is issued.
        lockedTrip.setDriverAccountId(null);
        lockedTrip.setAssignmentPinHash(null);
        lockedTrip.setAssignmentPinExpiresAt(null);
        lockedTrip.setAssignmentPinAttempts(0);
        trips.save(lockedTrip);
        String secret = DriverAccessCode.newSecret(random);
        TripDriverGrant grant = grants.saveAndFlush(TripDriverGrant.builder()
                .id(uniqueId()).tripId(lockedTrip.getId()).driverProfileId(profileId).carrierId(lockedTrip.getCarrierId())
                .assignmentVersion(lockedTrip.getAssignmentVersion()).secretHash(DriverAccessCode.hash(secret))
                .state(TripDriverGrant.ISSUED).expiresAt(now.plus(CODE_TTL)).issuedBy(issuedBy).issuedAt(now).build());
        return new Issued(grant.getId(), DriverAccessCode.format(grant.getId(), secret), grant.getExpiresAt(), grant.getAssignmentVersion());
    }

    /** UUIDv7 with a uniqueness check; the primary key constraint is the final guard. */
    UUID uniqueId() {
        for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
            UUID candidate = ids.next();
            if (!grants.existsById(candidate)) return candidate;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate an assignment ID; try again");
    }

    /**
     * Redeems a code into one driver session. A retry with the same code and request ID inside the replay
     * window returns the same session; any other second use is refused. Errors before the secret is
     * verified are identical so a guessed grant ID reveals nothing.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Exchange exchange(UUID grantId, String secret, UUID requestId) {
        if (grantId == null || secret == null || requestId == null) throw invalidCode();
        // Lock order trip -> grant. Nothing is loaded before the locks, so both rows are read fresh and a
        // concurrent redeem waits, then sees REDEEMED.
        UUID tripId = grants.findTripIdById(grantId).orElseThrow(DriverGrantService::invalidCode);
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(DriverGrantService::invalidCode);
        TripDriverGrant grant = grants.lockById(grantId).orElseThrow(DriverGrantService::invalidCode);
        if (!DriverAccessCode.matches(secret, grant.getSecretHash())) throw invalidCode();
        Instant now = clock.instant();
        if (TripDriverGrant.REDEEMED.equals(grant.getState())) {
            boolean sameRequest = requestId.equals(grant.getRedeemRequestId())
                    && grant.getRedeemedAt().plus(REDEEM_REPLAY_WINDOW).isAfter(now)
                    && grant.getSessionRevokedAt() == null
                    && grant.getDriverSessionId().equals(trip.getDriverSessionId());
            if (sameRequest) return exchangeOf(grant, true);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhận chuyến đã được sử dụng. Nhờ chủ xe cấp mã mới.");
        }
        if (TripDriverGrant.REVOKED.equals(grant.getState()))
            throw new ResponseStatusException(HttpStatus.GONE, "Mã nhận chuyến đã bị thay bằng mã khác. Nhờ chủ xe gửi mã mới.");
        if (!grant.getExpiresAt().isAfter(now))
            throw new ResponseStatusException(HttpStatus.GONE, "Mã nhận chuyến đã hết hạn. Nhờ chủ xe cấp mã mới.");
        if (trip.getAssignmentVersion() != grant.getAssignmentVersion()
                || !Objects.equals(trip.getDriverId(), grant.getDriverProfileId())
                || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phân công của chuyến đã thay đổi. Nhờ chủ xe cấp mã mới.");
        UUID sessionId = ids.next();
        grant.setState(TripDriverGrant.REDEEMED);
        grant.setRedeemRequestId(requestId);
        grant.setDriverSessionId(sessionId);
        grant.setRedeemedAt(now);
        grants.save(grant);
        trip.setDriverSessionId(sessionId);
        trip.setDriverAccountId(null);
        trips.save(trip);
        events.save(JourneyEvent.builder().trip(trip).actorId(sessionId).eventType(JourneyEventType.DRIVER_ACCEPTED)
                .status(trip.getStatus()).note("Tài xế nhận chuyến bằng mã nhận chuyến").build());
        return exchangeOf(grant, false);
    }

    private static Exchange exchangeOf(TripDriverGrant grant, boolean replay) {
        return new Exchange(grant.getDriverSessionId(), grant.getTripId(), grant.getDriverProfileId(), grant.getId(),
                grant.getAssignmentVersion(), grant.getCarrierId(), replay);
    }

    /** Live state of a session; identity refresh and media read rely on this, so it fails closed. */
    @Transactional(readOnly = true)
    public SessionStatus status(UUID sessionId) {
        TripDriverGrant grant = grants.findByDriverSessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver session not found"));
        Trip trip = trips.findById(grant.getTripId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver session not found"));
        boolean current = TripDriverGrant.REDEEMED.equals(grant.getState()) && grant.getSessionRevokedAt() == null
                && sessionId.equals(trip.getDriverSessionId()) && trip.getAssignmentVersion() == grant.getAssignmentVersion();
        boolean closed = trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED;
        Instant readUntil = closed && trip.getUpdatedAt() != null ? trip.getUpdatedAt().plus(DriverAccessPolicy.CLOSED_READ_GRACE) : null;
        boolean readable = current && (!closed || DriverAccessPolicy.withinGrace(trip, clock.instant()));
        boolean writable = current && !closed && trip.getStatus() != TripStatus.DELIVERED;
        return new SessionStatus(sessionId, trip.getId(), grant.getDriverProfileId(), grant.getId(), grant.getAssignmentVersion(),
                readable, writable, trip.getStatus().name(), readUntil);
    }

    /**
     * The fleet profile was deleted or is no longer verified: every code and session for it on open trips
     * stops at once (version bump), and a legacy account claim is dropped. The trip keeps its profile,
     * status, events and GPS; the carrier reassigns, or reissues after the profile is verified again.
     */
    @Transactional
    public int revokeProfile(UUID profileId, String reason) {
        Instant now = clock.instant();
        int revoked = 0;
        for (UUID tripId : trips.findIdsByDriverIdAndStatusNotIn(profileId, java.util.List.of(TripStatus.COMPLETED, TripStatus.CANCELLED))) {
            Trip trip = trips.findByIdForUpdate(tripId).orElse(null);
            if (trip == null || !profileId.equals(trip.getDriverId())
                    || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) continue;
            grants.revokeAllForTrip(tripId, now, reason);
            trackingSessions.stopForTrip(tripId, now);
            trip.setAssignmentVersion(trip.getAssignmentVersion() + 1);
            trip.setDriverSessionId(null);
            trip.setDriverAccountId(null);
            trips.save(trip);
            revoked++;
        }
        return revoked;
    }

    /** Idempotent logout/revoke: after commit, the session's access token and GPS uploads are refused. */
    @Transactional
    public void revokeSession(UUID sessionId, String reason) {
        UUID grantId = grants.findIdByDriverSessionId(sessionId).orElse(null);
        if (grantId == null) return;
        UUID tripId = grants.findTripIdById(grantId).orElse(null);
        if (tripId == null) return;
        Trip trip = trips.findByIdForUpdate(tripId).orElse(null);
        TripDriverGrant grant = grants.lockById(grantId).orElse(null);
        if (grant == null) return;
        Instant now = clock.instant();
        if (grant.getSessionRevokedAt() == null) {
            grant.setSessionRevokedAt(now);
            if (grant.getRevokeReason() == null) grant.setRevokeReason(reason);
            grants.save(grant);
        }
        if (trip != null && sessionId.equals(trip.getDriverSessionId())) {
            trip.setDriverSessionId(null);
            trips.save(trip);
        }
        trackingSessions.stopForDriverSession(sessionId, now);
    }

    /** Media read for a driver session: only proof/evidence objects attached to its own readable trip. */
    @Transactional(readOnly = true)
    public boolean canReadMedia(UUID sessionId, String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) return false;
        SessionStatus status;
        try {
            status = status(sessionId);
        } catch (ResponseStatusException missing) {
            return false;
        }
        if (!status.readable()) return false;
        String suffix = "/" + objectKey;
        return proofs.findByTripIdOrderByUploadedAtAsc(status.tripId()).stream()
                .anyMatch(proof -> proof.getImageUrl() != null && proof.getImageUrl().endsWith(suffix))
                || events.findByTripIdOrderByRecordedAtAsc(status.tripId()).stream()
                .anyMatch(event -> event.getEvidenceUrl() != null && event.getEvidenceUrl().endsWith(suffix));
    }

    static ResponseStatusException invalidCode() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mã nhận chuyến không đúng.");
    }
}
