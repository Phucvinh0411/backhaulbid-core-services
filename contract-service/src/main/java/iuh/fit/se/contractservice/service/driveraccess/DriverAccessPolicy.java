package iuh.fit.se.contractservice.service.driveraccess;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The one predicate for driver access. A code-login session must match the trip's current session, its
 * assignment version and fleet profile, and the trip in its token; a legacy driver account must be the
 * trip's claimed account. Checked on every request, so reassignment or logout takes effect immediately.
 */
@Component
public class DriverAccessPolicy {
    /** Closed trips stay readable for the same session for at most this long (still bounded by session expiry). */
    public static final Duration CLOSED_READ_GRACE = Duration.ofHours(24);
    public enum Mode { READ, WRITE }

    private final Clock clock;

    public DriverAccessPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean allows(Trip trip, UUID actor, AccountRole role, Mode mode) {
        if (role != AccountRole.DRIVER || actor == null) return false;
        DriverScope scope = DriverScope.current();
        if (!scope.isDriverSession()) return trip.getDriverSessionId() == null && actor.equals(trip.getDriverAccountId());
        boolean current = actor.equals(trip.getDriverSessionId())
                && trip.getId().equals(scope.tripId())
                && scope.assignmentVersion() != null && scope.assignmentVersion() == trip.getAssignmentVersion()
                && Objects.equals(trip.getDriverId(), scope.profileId());
        if (!current) return false;
        if (!closed(trip.getStatus())) return true;
        return mode == Mode.READ && withinGrace(trip, clock.instant());
    }

    /** Actor whose GPS counts as the vehicle's position: the current session, else the claimed account. */
    public static UUID currentDriverActor(Trip trip) {
        return trip.getDriverSessionId() != null ? trip.getDriverSessionId() : trip.getDriverAccountId();
    }

    public static boolean isDriverSession() {
        return DriverScope.current().isDriverSession();
    }

    static boolean closed(TripStatus status) {
        return status == TripStatus.COMPLETED || status == TripStatus.CANCELLED;
    }

    public static boolean withinGrace(Trip trip, Instant now) {
        Instant closedAt = trip.getUpdatedAt();
        return closedAt != null && now.isBefore(closedAt.plus(CLOSED_READ_GRACE));
    }
}
