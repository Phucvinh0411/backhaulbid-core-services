package iuh.fit.se.contractservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "trip_tracking_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TripTrackingSession {
    @Id private UUID id;
    @Column(name = "trip_id", nullable = false) private UUID tripId;
    /** Exactly one of driverAccountId (legacy account) and driverSessionId (code login) is set. */
    @Column(name = "driver_account_id") private UUID driverAccountId;
    @Column(name = "driver_session_id") private UUID driverSessionId;
    @Column(nullable = false, length = 12) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "last_received_at") private Instant lastReceivedAt;
}
