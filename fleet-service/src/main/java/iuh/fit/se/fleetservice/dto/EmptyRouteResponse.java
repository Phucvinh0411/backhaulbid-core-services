package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmptyRouteResponse(
        UUID id,
        String truckId,
        String truckType,
        String companyId,
        Double availableCapacity,
        String origin,
        Double latitude,
        Double longitude,
        String destination,
        Double destLatitude,
        Double destLongitude,
        LocalDateTime expectedEmptyTime,
        LocalDateTime expectedArrivalTime,
        Integer searchRadius,
        EmptyRouteStatus status
) {
}
