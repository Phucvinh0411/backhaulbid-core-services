package iuh.fit.se.fleetservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carrier_reputation_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reputation_trip_tier", columnNames = {"trip_id", "tier"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierReputationEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private int tier;

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    @Column(name = "score_after", nullable = false)
    private int scoreAfter;

    @Column(nullable = false, length = 300)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
