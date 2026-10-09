package iuh.fit.se.contractservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "trip_route_point_audits")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TripRoutePointAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "trip_id", nullable = false)
    private UUID tripId;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(name = "route_version", nullable = false)
    private long routeVersion;
    @Column(name = "previous_pickup_latitude")
    private Double previousPickupLatitude;
    @Column(name = "previous_pickup_longitude")
    private Double previousPickupLongitude;
    @Column(name = "previous_delivery_latitude")
    private Double previousDeliveryLatitude;
    @Column(name = "previous_delivery_longitude")
    private Double previousDeliveryLongitude;
    @Column(name = "pickup_latitude", nullable = false)
    private Double pickupLatitude;
    @Column(name = "pickup_longitude", nullable = false)
    private Double pickupLongitude;
    @Column(name = "delivery_latitude", nullable = false)
    private Double deliveryLatitude;
    @Column(name = "delivery_longitude", nullable = false)
    private Double deliveryLongitude;
    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;
}
