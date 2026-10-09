package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.repository.DriverProfileRepository;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FleetStatisticsService {
    private final VehicleRepository vehicles;
    private final DriverProfileRepository drivers;
    private final CarrierReputationService reputationService;

    public Map<String, Object> mine(UUID carrierId, String role) {
        requireRole(role, "CARRIER");
        Map<String, Long> byVehicleStatus = new LinkedHashMap<>();
        for (VehicleStatus status : VehicleStatus.values()) byVehicleStatus.put(status.name(), vehicles.countByCarrierIdAndStatus(carrierId, status));
        var reputation = reputationService.getScore(carrierId);
        return Map.of(
                "vehicleCount", vehicles.countByCarrierId(carrierId),
                "activeVehicleCount", byVehicleStatus.get(VehicleStatus.VERIFIED.name()),
                "byVehicleStatus", byVehicleStatus,
                "driverCount", drivers.countByCarrierId(carrierId),
                "verifiedDriverCount", drivers.countByCarrierIdAndStatus(carrierId, VerificationStatus.VERIFIED),
                "reputation", reputation.score()
        );
    }

    public Map<String, Object> admin(String role) {
        requireRole(role, "ADMIN");
        Map<String, Long> vehicleStatuses = statusCounts(vehicles.countAllByStatus(), VehicleStatus.values());
        Map<String, Long> driverStatuses = statusCounts(drivers.countAllByStatus(), VerificationStatus.values());
        return Map.of(
                "vehicleCount", vehicleStatuses.values().stream().mapToLong(Long::longValue).sum(),
                "vehicleByStatus", vehicleStatuses,
                "driverCount", driverStatuses.values().stream().mapToLong(Long::longValue).sum(),
                "driverByStatus", driverStatuses,
                "carrierCount", vehicles.countDistinctCarriers()
        );
    }

    private static <E extends Enum<E>> Map<String, Long> statusCounts(java.util.List<Object[]> rows, E[] statuses) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (E status : statuses) values.put(status.name(), 0L);
        rows.forEach(row -> values.merge(String.valueOf(row[0]), ((Number) row[1]).longValue(), Long::sum));
        return values;
    }

    private static void requireRole(String role, String expected) {
        if (!expected.equalsIgnoreCase(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, expected + " role required");
    }
}
