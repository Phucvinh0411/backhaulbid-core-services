package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journey_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private JourneyEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TripStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
