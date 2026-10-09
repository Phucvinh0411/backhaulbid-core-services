package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.TripStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "driver_account_id")
    private UUID driverAccountId;

    /** Increases on every assignment or reissue; grants and driver sessions of older versions stop matching. */
    @Column(name = "assignment_version", nullable = false)
    @Builder.Default
    private long assignmentVersion = 0;

    /** Driver session (code login) currently allowed on this trip; separate from driverAccountId. */
    @Column(name = "driver_session_id")
    private UUID driverSessionId;

    @Column(name = "assignment_pin_expires_at")
    private Instant assignmentPinExpiresAt;

    @Column(name = "assignment_pin_attempts", nullable = false)
    private int assignmentPinAttempts;

    @Column(name = "auction_id")
    private String auctionId;

    @Column(name = "award_attempt_id", unique = true, length = 100)
    private String awardAttemptId;

    @Column(name = "winning_bid_id", length = 100)
    private String winningBidId;

    @Column(name = "expected_delivery_at")
    private Instant expectedDeliveryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "deposit_hold_id", length = 100)
    private String depositHoldId;

    @Column(name = "deposit_amount", precision = 19, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_released_at")
    private Instant depositReleasedAt;

    @Column(name = "assignment_pin_hash", length = 64)
    private String assignmentPinHash;

    @Column(name = "pickup_location", nullable = false, length = 500)
    private String pickupLocation;

    @Column(name = "delivery_location", nullable = false, length = 500)
    private String deliveryLocation;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "pickup_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "pickup_longitude")),
            @AttributeOverride(name = "label", column = @Column(name = "pickup_label", length = 120)),
            @AttributeOverride(name = "address", column = @Column(name = "pickup_address", length = 1000)),
            @AttributeOverride(name = "source", column = @Column(name = "pickup_coordinate_source", length = 30))
    })
    private TripRouteLocation pickupPoint;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "delivery_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "delivery_longitude")),
            @AttributeOverride(name = "label", column = @Column(name = "delivery_label", length = 120)),
            @AttributeOverride(name = "address", column = @Column(name = "delivery_address", length = 1000)),
            @AttributeOverride(name = "source", column = @Column(name = "delivery_coordinate_source", length = 30))
    })
    private TripRouteLocation deliveryPoint;

    @Column(name = "route_version", nullable = false)
    private long routeVersion;

    @Column(name = "award_route_snapshot_hash", length = 64)
    private String awardRouteSnapshotHash;

    @Column(name = "agreed_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal agreedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripStatus status;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Builder.Default
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackingLog> trackingLogs = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryProof> deliveryProofs = new ArrayList<>();

    @OneToOne(mappedBy = "trip", fetch = FetchType.LAZY)
    private Contract contract;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void startTrip() {
        if (status != TripStatus.WAITING_PICKUP) {
            throw new IllegalStateException("Only waiting trips can be started");
        }
        status = TripStatus.PICKED_UP;
    }

    public void updateStatus(TripStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Trip status is required");
        }
        status = newStatus;
        if (newStatus == TripStatus.DELIVERED && deliveredAt == null) {
            deliveredAt = Instant.now();
        }
    }

    public void completeTrip() {
        if (status != TripStatus.DELIVERED) {
            throw new IllegalStateException("Trip must be delivered before completion");
        }
        status = TripStatus.COMPLETED;
    }

    public void cancel(String reason) {
        if (status == TripStatus.COMPLETED) {
            throw new IllegalStateException("Completed trip cannot be cancelled");
        }
        status = TripStatus.CANCELLED;
        cancellationReason = reason;
    }
}
