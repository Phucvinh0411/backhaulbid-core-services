package iuh.fit.se.fleetservice.domain.entity;

import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "empty_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmptyRoute {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "truck_id", nullable = false)
    private String truckId;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "expected_empty_time", nullable = false)
    private LocalDateTime expectedEmptyTime;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmptyRouteStatus status;
}
