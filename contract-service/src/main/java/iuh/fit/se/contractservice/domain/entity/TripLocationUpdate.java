package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.TripStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_location_updates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripLocationUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 160)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripLocationSource source;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TripStatus status;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
