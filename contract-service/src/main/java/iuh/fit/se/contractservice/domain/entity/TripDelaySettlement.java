package iuh.fit.se.contractservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_delay_settlements", uniqueConstraints =
        @UniqueConstraint(name = "uk_trip_delay_settlement_trip_tier", columnNames = {"trip_id", "tier"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDelaySettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private int tier;

    @Column(name = "late_minutes", nullable = false)
    private long lateMinutes;

    @Column(name = "cumulative_penalty_percent", nullable = false)
    private int cumulativePenaltyPercent;

    @Column(name = "total_compensation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCompensationAmount;

    @Column(name = "incremental_compensation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal incrementalCompensationAmount;

    @Column(name = "points_deducted", nullable = false)
    private int pointsDeducted;

    @Column(name = "wallet_status", nullable = false, length = 20)
    @Builder.Default
    private String walletStatus = "PENDING";

    @Column(name = "reputation_status", nullable = false, length = 20)
    @Builder.Default
    private String reputationStatus = "PENDING";

    @Column(name = "notification_status", nullable = false, length = 20)
    @Builder.Default
    private String notificationStatus = "PENDING";

    @Column(name = "overall_status", nullable = false, length = 20)
    @Builder.Default
    private String overallStatus = "PENDING";

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
