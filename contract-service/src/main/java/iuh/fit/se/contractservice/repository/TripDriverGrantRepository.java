package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripDriverGrant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripDriverGrantRepository extends JpaRepository<TripDriverGrant, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from TripDriverGrant g where g.id = :id")
    Optional<TripDriverGrant> lockById(@Param("id") UUID id);

    Optional<TripDriverGrant> findByDriverSessionId(UUID driverSessionId);

    /** Scalar lookups load no entity, so the later row lock always reads fresh state. */
    @Query("select g.tripId from TripDriverGrant g where g.id = :id")
    Optional<UUID> findTripIdById(@Param("id") UUID id);

    @Query("select g.id from TripDriverGrant g where g.driverSessionId = :session")
    Optional<UUID> findIdByDriverSessionId(@Param("session") UUID session);

    List<TripDriverGrant> findByTripIdOrderByIssuedAtDesc(UUID tripId);

    /** Revokes every unrevoked grant of a trip (used under the trip row lock). */
    @Modifying(flushAutomatically = true)
    @Query("update TripDriverGrant g set g.state = 'REVOKED', g.revokedAt = :now, g.revokeReason = :reason, "
            + "g.sessionRevokedAt = case when g.driverSessionId is not null and g.sessionRevokedAt is null then :now else g.sessionRevokedAt end "
            + "where g.tripId = :trip and g.state <> 'REVOKED'")
    int revokeAllForTrip(@Param("trip") UUID trip, @Param("now") Instant now, @Param("reason") String reason);
}
