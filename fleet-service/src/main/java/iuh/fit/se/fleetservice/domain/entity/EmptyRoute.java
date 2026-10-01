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

    // ───── Phương tiện ─────
    @Column(name = "truck_id", nullable = false)
    private String truckId;

    @Column(name = "truck_type", length = 60)
    private String truckType;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    // ───── Tải trọng ─────
    /** Tải trọng còn trống (tấn) – do chủ xe khai báo */
    @Column(name = "available_capacity")
    private Double availableCapacity;

    // ───── Điểm xuất phát (A) ─────
    @Column(name = "origin")
    private String origin;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    // ───── Điểm đến (B) ─────
    @Column(name = "destination")
    private String destination;

    @Column(name = "dest_latitude")
    private Double destLatitude;

    @Column(name = "dest_longitude")
    private Double destLongitude;

    // ───── Thời gian ─────
    @Column(name = "expected_empty_time", nullable = false)
    private LocalDateTime expectedEmptyTime;

    @Column(name = "expected_arrival_time")
    private LocalDateTime expectedArrivalTime;

    // ───── Bán kính tìm kiếm ─────
    /** Bán kính tìm kiếm lô hàng xung quanh điểm xuất phát (km) – mặc định 50 km */
    @Column(name = "search_radius")
    @Builder.Default
    private Integer searchRadius = 50;

    // ───── Trạng thái ─────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmptyRouteStatus status;
}
