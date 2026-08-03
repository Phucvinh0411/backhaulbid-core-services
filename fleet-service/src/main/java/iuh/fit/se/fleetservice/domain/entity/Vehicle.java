package iuh.fit.se.fleetservice.domain.entity;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.domain.strategy.ContainerValidationStrategy;
import iuh.fit.se.fleetservice.domain.strategy.TruckValidationStrategy;
import iuh.fit.se.fleetservice.domain.strategy.VehicleValidationStrategy;
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
@Table(name = "vehicles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "license_plate", nullable = false, unique = true, length = 30)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 40)
    private VehicleType vehicleType;

    @Column(name = "body_type", length = 100)
    private String bodyType;

    @Column(name = "payload_capacity", nullable = false, precision = 12, scale = 2)
    private BigDecimal payloadCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleStatus status;

    @OneToOne(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private VehicleVerification verification;

    @Builder.Default
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isEligible(BigDecimal requiredPayload, VehicleType requiredType) {
        if (vehicleType == null || requiredType == null) {
            return false;
        }
        VehicleValidationStrategy validator = vehicleType == VehicleType.CONTAINER_TRACTOR
                ? new ContainerValidationStrategy()
                : new TruckValidationStrategy();
        return validator.validate(this, requiredPayload, requiredType);
    }
}
