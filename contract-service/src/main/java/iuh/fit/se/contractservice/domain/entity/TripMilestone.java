package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.MilestoneStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "trip_milestones",
    indexes = {
        @Index(name = "idx_trip_milestones_trip_id", columnList = "trip_id"),
        @Index(name = "idx_trip_milestones_status",  columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "milestone_name", nullable = false, length = 255)
    private String milestoneName;

    /** Toạ độ mục tiêu — được cấu hình sẵn khi tạo chuyến. */
    @Column(name = "target_lat", nullable = false)
    private Double targetLat;

    @Column(name = "target_lng", nullable = false)
    private Double targetLng;

    /** Toạ độ thực tế — được ghi lại khi tài xế check-in. */
    @Column(name = "actual_lat")
    private Double actualLat;

    @Column(name = "actual_lng")
    private Double actualLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @Column(name = "reached_at")
    private Instant reachedAt;

    /** Thứ tự hiển thị trên timeline tài xế. */
    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
