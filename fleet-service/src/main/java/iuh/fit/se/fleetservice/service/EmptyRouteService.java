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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmptyRouteService {

    private final EmptyRouteRepository emptyRouteRepository;
    private final iuh.fit.se.fleetservice.repository.VehicleRepository vehicleRepository;

    /**
     * Chủ nhà xe đăng ký tuyến chạy rỗng
     */
    @Transactional
    public EmptyRoute createEmptyRoute(EmptyRouteRequest request, String ownerCompanyId) {
        log.info("Đăng ký tuyến chạy rỗng mới cho xe: {}", request.getTruckId());

        Vehicle vehicle = resolveOwnedVehicle(request.getTruckId(), ownerCompanyId);
        
        EmptyRoute emptyRoute = EmptyRoute.builder()
            .truckId(vehicle.getLicensePlate())
                .companyId(ownerCompanyId)
                .expectedEmptyTime(request.getExpectedEmptyTime())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .status(EmptyRouteStatus.PENDING)
                .build();
                
        return emptyRouteRepository.save(emptyRoute);
    }

    @Transactional(readOnly = true)
    public java.util.List<EmptyRoute> listMine(String ownerCompanyId) {
        return emptyRouteRepository.findByCompanyIdOrderByExpectedEmptyTimeAsc(ownerCompanyId);
    }

    @Transactional(readOnly = true)
    public java.util.List<EmptyRoute> findMatchingRoutes(String origin, String destination, java.util.Map<String, Object> auctionData) {
        log.info("Tìm kiếm xe rỗng chiều nâng cao cho lộ trình: {} -> {}", origin, destination);

        String originKeyword = extractProvince(origin);
        String destinationKeyword = extractProvince(destination);

        List<EmptyRoute> potentialMatches = emptyRouteRepository
            .findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
                originKeyword, 
                destinationKeyword, 
                EmptyRouteStatus.PENDING
            );

        if (potentialMatches.isEmpty()) {
            return List.of();
        }

        BigDecimal requiredWeight = parseBigDecimal(auctionData.get("weight")).orElse(null);
        VehicleType requiredType = parseVehicleType(auctionData.get("vehicleTypeRequired")).orElse(null);
        LocalDateTime latestPickup = parseLatestPickup(auctionData).orElse(null);

        List<ScoredRoute> scoredMatches = new ArrayList<>();

        for (EmptyRoute route : potentialMatches) {
            try {
                Optional<Vehicle> vehicleOpt = vehicleRepository.findByLicensePlateIgnoreCase(route.getTruckId());
                if (vehicleOpt.isEmpty()) {
                    log.debug("Loại route {} vì không tìm thấy xe tương ứng biển số {}", route.getId(), route.getTruckId());
                    continue;
                }

                Vehicle vehicle = vehicleOpt.get();
                if (vehicle.getStatus() != VehicleStatus.VERIFIED) {
                    log.debug("Loại route {} vì xe {} chưa được xác minh (status={})",
                              route.getId(), route.getTruckId(), vehicle.getStatus());
                    continue;
                }

                // Loại bỏ tuyến rỗng đã quá hạn
                if (route.getExpectedEmptyTime().isBefore(LocalDateTime.now())) {
                    log.debug("Loại route {} do thời gian rỗng ({}) đã quá hạn",
                              route.getId(), route.getExpectedEmptyTime());
                    continue;
                }

                if (latestPickup != null && route.getExpectedEmptyTime().isAfter(latestPickup)) {
                    log.debug("Loại route {} do thời gian rỗng ({}) trễ hơn latestPickup ({})", route.getId(), route.getExpectedEmptyTime(), latestPickup);
                    continue;
                }

                if (requiredWeight != null && vehicle.getPayloadCapacity().compareTo(requiredWeight) < 0) {
                    log.debug("Loại route {} do tải trọng xe ({}) nhỏ hơn yêu cầu ({})", route.getId(), vehicle.getPayloadCapacity(), requiredWeight);
                    continue;
                }

                if (requiredType != null && vehicle.getVehicleType() != requiredType) {
                    log.debug("Loại route {} do loại xe ({}) không khớp yêu cầu ({})", route.getId(), vehicle.getVehicleType(), requiredType);
                    continue;
                }

                double score = computeScore(route, vehicle, requiredWeight, latestPickup);
                scoredMatches.add(new ScoredRoute(route, score));
            } catch (Exception e) {
                log.error("Lỗi khi đánh giá tiêu chí nâng cao cho route {}: {}", route.getId(), e.getMessage());
            }
        }

        scoredMatches.sort(Comparator.comparingDouble(ScoredRoute::score).reversed());
        return scoredMatches.stream().map(ScoredRoute::route).toList();
    }

    private Vehicle resolveOwnedVehicle(String truckRef, String ownerCompanyId) {
        Vehicle vehicle = findVehicleByReference(truckRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy xe theo mã/biển số"));

        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerCompanyId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định danh chủ xe không hợp lệ");
        }

        if (!ownerId.equals(vehicle.getCarrierId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Xe không thuộc quyền sở hữu của tài khoản hiện tại");
        }

        if (vehicle.getStatus() != VehicleStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Xe chưa được xác minh (trạng thái: " + vehicle.getStatus()
                + "). Chỉ xe đã VERIFIED mới được khai báo tuyến rỗng.");
        }

        return vehicle;
    }

    private Optional<Vehicle> findVehicleByReference(String truckRef) {
        if (truckRef == null || truckRef.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalized = truckRef.trim();
        try {
            return vehicleRepository.findById(UUID.fromString(normalized))
                    .or(() -> vehicleRepository.findByLicensePlateIgnoreCase(normalized));
        } catch (IllegalArgumentException ex) {
            return vehicleRepository.findByLicensePlateIgnoreCase(normalized);
        }
    }

    private Optional<BigDecimal> parseBigDecimal(Object raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(raw.toString()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<VehicleType> parseVehicleType(Object raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(VehicleType.valueOf(raw.toString().trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseLatestPickup(Map<String, Object> auctionData) {
        Object raw = auctionData.get("latestPickup");
        if (raw == null) return Optional.empty();

        String value = raw.toString().trim();
        if (value.isEmpty()) return Optional.empty();

        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Optional.of(Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime());
        } catch (DateTimeParseException ignored) {
        }

        return Optional.empty();
    }

    private double computeScore(
            EmptyRoute route,
            Vehicle vehicle,
            BigDecimal requiredWeight,
            LocalDateTime latestPickup
    ) {
        double score = 1.0;

        if (requiredWeight != null && requiredWeight.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = vehicle.getPayloadCapacity().doubleValue() / requiredWeight.doubleValue();
            score += Math.max(0.0, 2.0 - Math.min(ratio, 3.0));
        }

        if (latestPickup != null) {
            long minutesGap = Math.max(0, java.time.Duration.between(route.getExpectedEmptyTime(), latestPickup).toMinutes());
            score += Math.min(minutesGap / 60.0, 2.0);
        }

        return score;
    }

    private String extractProvince(String fullLocation) {
        if (fullLocation == null) return "";
        String trimmed = fullLocation.trim();
        int separator = trimmed.lastIndexOf(" - ");
        if (separator <= 0) {
            return trimmed;
        }
        return trimmed.substring(0, separator).trim();
    }

    private record ScoredRoute(EmptyRoute route, double score) {}
}
