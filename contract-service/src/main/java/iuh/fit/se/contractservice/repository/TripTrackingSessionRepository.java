package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripTrackingSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface TripTrackingSessionRepository extends JpaRepository<TripTrackingSession, UUID> {
    Optional<TripTrackingSession> findFirstByTripIdAndStatus(UUID tripId, String status);
    Optional<TripTrackingSession> findFirstByTripIdOrderByStartedAtDesc(UUID tripId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TripTrackingSession s where s.id = :id")
    Optional<TripTrackingSession> lockById(@Param("id") UUID id);
    @Modifying @Query("update TripTrackingSession s set s.status='STOPPED', s.endedAt=:now where s.driverAccountId=:driver and s.status='ACTIVE'")
    int stopForDriver(@Param("driver") UUID driver, @Param("now") Instant now);
    @Modifying @Query("update TripTrackingSession s set s.status='STOPPED', s.endedAt=:now where s.driverSessionId=:session and s.status='ACTIVE'")
    int stopForDriverSession(@Param("session") UUID session, @Param("now") Instant now);
    @Modifying @Query("update TripTrackingSession s set s.status='STOPPED', s.endedAt=:now where s.tripId=:trip and s.status='ACTIVE'")
    int stopForTrip(@Param("trip") UUID trip, @Param("now") Instant now);
    @Modifying @Query(value = "update trip_tracking_sessions s set status='STOPPED',ended_at=:now "
            + "from trips t where s.trip_id=t.id and s.status='ACTIVE' and "
            + "(t.status in ('DELIVERED','COMPLETED','CANCELLED') "
            + "or (s.driver_session_id is null and t.driver_account_id is distinct from s.driver_account_id) "
            + "or (s.driver_session_id is not null and t.driver_session_id is distinct from s.driver_session_id))", nativeQuery = true)
    int stopInvalid(@Param("now") Instant now);
}
