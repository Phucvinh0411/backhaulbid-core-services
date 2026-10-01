package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.repository.EmptyRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmptyRouteService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int    DEFAULT_SEARCH_RADIUS_KM = 50;

    private final EmptyRouteRepository emptyRouteRepository;
    private final iuh.fit.se.fleetservice.repository.VehicleRepository vehicleRepository;

    // ══════════════════════════════════════════════════════════════════
    //  Chủ nhà xe đăng ký tuyến chạy rỗng
    // ══════════════════════════════════════════════════════════════════
    @Transactional
    public EmptyRoute createEmptyRoute(EmptyRouteRequest request, String ownerCompanyId) {
        log.info("Đăng ký tuyến chạy rỗng mới cho xe: {}", request.getTruckId());

        Vehicle vehicle = resolveOwnedVehicle(request.getTruckId(), ownerCompanyId);

        // Validate thời gian: arrival phải sau departure
        if (request.getExpectedArrivalTime() != null
                && request.getExpectedArrivalTime().isBefore(request.getExpectedEmptyTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian dự kiến đến phải sau thời gian bắt đầu rỗng chiều.");
        }

        EmptyRoute emptyRoute = EmptyRoute.builder()
                .truckId(vehicle.getLicensePlate())
                .truckType(vehicle.getVehicleType() != null ? vehicle.getVehicleType().name() : request.getTruckType())
                .companyId(ownerCompanyId)
                .availableCapacity(request.getAvailableCapacity() != null
                        ? request.getAvailableCapacity()
                        : (vehicle.getPayloadCapacity() != null ? vehicle.getPayloadCapacity().doubleValue() : null))
                .origin(request.getOrigin())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .destination(request.getDestination())
                .destLatitude(request.getDestLatitude())
                .destLongitude(request.getDestLongitude())
                .expectedEmptyTime(request.getExpectedEmptyTime())
                .expectedArrivalTime(request.getExpectedArrivalTime())
                .searchRadius(request.getSearchRadius() != null ? request.getSearchRadius() : DEFAULT_SEARCH_RADIUS_KM)
                .status(EmptyRouteStatus.PENDING)
                .build();

        return emptyRouteRepository.save(emptyRoute);
    }

    @Transactional(readOnly = true)
    public List<EmptyRoute> listMine(String ownerCompanyId) {
        return emptyRouteRepository.findByCompanyIdOrderByExpectedEmptyTimeAsc(ownerCompanyId);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Tìm xe rỗng phù hợp với một phiên đấu giá – dựa trên tọa độ + GIS
    // ══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<EmptyRoute> findMatchingRoutes(String origin, String destination, Map<String, Object> auctionData) {
        log.info("Tìm kiếm xe rỗng chiều cho lộ trình: {} -> {}", origin, destination);

        // Tọa độ điểm bốc hàng (origin của đơn hàng)
        Double pickupLat = parseDouble(auctionData.get("pickupLat")).orElse(null);
        Double pickupLng = parseDouble(auctionData.get("pickupLng")).orElse(null);

        // Tọa độ điểm giao hàng (destination của đơn hàng)
        Double dropLat   = parseDouble(auctionData.get("dropLat")).orElse(null);
        Double dropLng   = parseDouble(auctionData.get("dropLng")).orElse(null);

        BigDecimal   requiredWeight = parseBigDecimal(auctionData.get("weight")).orElse(null);
        VehicleType  requiredType   = parseVehicleType(auctionData.get("vehicleTypeRequired")).orElse(null);
        LocalDateTime latestPickup  = parseLatestPickup(auctionData).orElse(null);

        // Lấy tất cả tuyến rỗng đang PENDING
        List<EmptyRoute> candidates = emptyRouteRepository
                .findByStatusOrderByExpectedEmptyTimeAsc(EmptyRouteStatus.PENDING);

        List<ScoredRoute> scoredMatches = new ArrayList<>();

        for (EmptyRoute route : candidates) {
            try {
                // ── 1. Xe phải tồn tại và đã được VERIFIED ──
                Optional<Vehicle> vehicleOpt = vehicleRepository.findByLicensePlateIgnoreCase(route.getTruckId());
                if (vehicleOpt.isEmpty()) {
                    log.debug("Loại route {} - không tìm thấy xe {}", route.getId(), route.getTruckId());
                    continue;
                }
                Vehicle vehicle = vehicleOpt.get();
                if (vehicle.getStatus() != VehicleStatus.VERIFIED) {
                    log.debug("Loại route {} - xe {} chưa VERIFIED", route.getId(), route.getTruckId());
                    continue;
                }

                // ── 2. Thời gian xe rỗng chưa quá hạn ──
                if (route.getExpectedEmptyTime().isBefore(LocalDateTime.now())) {
                    log.debug("Loại route {} - thời gian rỗng đã quá hạn", route.getId());
                    continue;
                }

                // ── 3. Thời gian xe rỗng không được trễ hơn latestPickup ──
                if (latestPickup != null && route.getExpectedEmptyTime().isAfter(latestPickup)) {
                    log.debug("Loại route {} - thời gian rỗng sau latestPickup", route.getId());
                    continue;
                }

                // ── 4. Tải trọng phải đủ ──
                double routeCapacity = resolveCapacity(route, vehicle);
                if (requiredWeight != null && routeCapacity < requiredWeight.doubleValue()) {
                    log.debug("Loại route {} - tải trọng {}<{} tấn", route.getId(), routeCapacity, requiredWeight);
                    continue;
                }

                // ── 5. Loại xe phải khớp ──
                if (requiredType != null && vehicle.getVehicleType() != requiredType) {
                    log.debug("Loại route {} - loại xe không khớp", route.getId());
                    continue;
                }

                // ── 6. GIS: điểm xuất phát xe phải nằm trong bán kính của lộ trình đơn hàng ──
                if (pickupLat != null && pickupLng != null) {
                    int radius = route.getSearchRadius() != null ? route.getSearchRadius() : DEFAULT_SEARCH_RADIUS_KM;
                    double distanceToPickup = haversineKm(
                            route.getLatitude(), route.getLongitude(),
                            pickupLat, pickupLng);
                    if (distanceToPickup > radius) {
                        log.debug("Loại route {} - khoảng cách đến điểm bốc {:.1f} km > bán kính {} km",
                                route.getId(), distanceToPickup, radius);
                        continue;
                    }
                }

                // ── 7. GIS: điểm đến xe phải gần điểm giao hàng ──
                boolean destMatch = true;
                double distToDrop = Double.MAX_VALUE;
                if (dropLat != null && dropLng != null
                        && route.getDestLatitude() != null && route.getDestLongitude() != null) {
                    distToDrop = haversineKm(
                            route.getDestLatitude(), route.getDestLongitude(),
                            dropLat, dropLng);
                    // Cho phép sai lệch điểm đến tối đa 100 km (có thể cấu hình)
                    if (distToDrop > 100) {
                        log.debug("Loại route {} - khoảng cách điểm đến {:.1f} km > 100 km", route.getId(), distToDrop);
                        destMatch = false;
                    }
                }
                if (!destMatch) continue;

                double score = computeScore(route, vehicle, requiredWeight, latestPickup, distToDrop);
                scoredMatches.add(new ScoredRoute(route, score));

            } catch (Exception e) {
                log.error("Lỗi khi đánh giá route {}: {}", route.getId(), e.getMessage());
            }
        }

        scoredMatches.sort(Comparator.comparingDouble(ScoredRoute::score).reversed());
        return scoredMatches.stream().map(ScoredRoute::route).toList();
    }

    // ══════════════════════════════════════════════════════════════════
    //  GIS – Haversine distance (km)
    // ══════════════════════════════════════════════════════════════════
    /**
     * Tính khoảng cách đường chim bay (km) giữa hai điểm theo công thức Haversine.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c    = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════

    private Vehicle resolveOwnedVehicle(String truckRef, String ownerCompanyId) {
        Vehicle vehicle = findVehicleByReference(truckRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Không tìm thấy xe theo mã/biển số: " + truckRef));

        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerCompanyId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định danh chủ xe không hợp lệ");
        }

        if (!ownerId.equals(vehicle.getCarrierId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Xe không thuộc quyền sở hữu của tài khoản hiện tại");
        }

        if (vehicle.getStatus() != VehicleStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Xe chưa được xác minh (trạng thái: " + vehicle.getStatus()
                    + "). Chỉ xe đã VERIFIED mới được khai báo tuyến rỗng.");
        }

        return vehicle;
    }

    private Optional<Vehicle> findVehicleByReference(String truckRef) {
        if (truckRef == null || truckRef.isBlank()) return Optional.empty();
        String normalized = truckRef.trim();
        try {
            return vehicleRepository.findById(UUID.fromString(normalized))
                    .or(() -> vehicleRepository.findByLicensePlateIgnoreCase(normalized));
        } catch (IllegalArgumentException ex) {
            return vehicleRepository.findByLicensePlateIgnoreCase(normalized);
        }
    }

    /**
     * Ưu tiên lấy available_capacity từ route (chủ xe khai báo),
     * fallback về payload_capacity của phương tiện trong DB.
     */
    private double resolveCapacity(EmptyRoute route, Vehicle vehicle) {
        if (route.getAvailableCapacity() != null && route.getAvailableCapacity() > 0) {
            return route.getAvailableCapacity();
        }
        return vehicle.getPayloadCapacity() != null
                ? vehicle.getPayloadCapacity().doubleValue()
                : 0.0;
    }

    private double computeScore(
            EmptyRoute route,
            Vehicle vehicle,
            BigDecimal requiredWeight,
            LocalDateTime latestPickup,
            double distToDrop
    ) {
        double score = 1.0;

        // Điểm 1: Tải trọng gần sát yêu cầu (không quá dư thừa)
        if (requiredWeight != null && requiredWeight.compareTo(BigDecimal.ZERO) > 0) {
            double capacity = resolveCapacity(route, vehicle);
            double ratio = capacity / requiredWeight.doubleValue();
            score += Math.max(0.0, 2.0 - Math.min(ratio, 3.0));
        }

        // Điểm 2: Thời gian xe rỗng càng gần latestPickup càng tốt (linh hoạt)
        if (latestPickup != null) {
            long minutesGap = Math.max(0,
                    java.time.Duration.between(route.getExpectedEmptyTime(), latestPickup).toMinutes());
            score += Math.min(minutesGap / 60.0, 2.0);
        }

        // Điểm 3: Khoảng cách điểm đến càng gần càng tốt
        if (distToDrop != Double.MAX_VALUE) {
            score += Math.max(0.0, 2.0 - distToDrop / 50.0);
        }

        return score;
    }

    private Optional<BigDecimal> parseBigDecimal(Object raw) {
        if (raw == null) return Optional.empty();
        try { return Optional.of(new BigDecimal(raw.toString())); }
        catch (NumberFormatException ex) { return Optional.empty(); }
    }

    private Optional<Double> parseDouble(Object raw) {
        if (raw == null) return Optional.empty();
        try { return Optional.of(Double.parseDouble(raw.toString())); }
        catch (NumberFormatException ex) { return Optional.empty(); }
    }

    private Optional<VehicleType> parseVehicleType(Object raw) {
        if (raw == null) return Optional.empty();
        try { return Optional.of(VehicleType.valueOf(raw.toString().trim().toUpperCase())); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }

    private Optional<LocalDateTime> parseLatestPickup(Map<String, Object> auctionData) {
        Object raw = auctionData.get("latestPickup");
        if (raw == null) return Optional.empty();
        String value = raw.toString().trim();
        if (value.isEmpty()) return Optional.empty();

        try { return Optional.of(LocalDateTime.parse(value)); }
        catch (DateTimeParseException ignored) {}

        try { return Optional.of(Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime()); }
        catch (DateTimeParseException ignored) {}

        return Optional.empty();
    }

    private record ScoredRoute(EmptyRoute route, double score) {}
}
