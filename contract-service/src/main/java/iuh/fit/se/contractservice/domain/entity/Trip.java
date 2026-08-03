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

    @Column(name = "pickup_location", nullable = false, length = 500)
    private String pickupLocation;

    @Column(name = "delivery_location", nullable = false, length = 500)
    private String deliveryLocation;

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
