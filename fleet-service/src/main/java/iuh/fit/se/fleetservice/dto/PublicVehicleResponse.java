package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicVehicleResponse(
        UUID id,
        String licensePlate,
        VehicleType vehicleType,
        String bodyType,
        BigDecimal payloadCapacity,
        VehicleStatus status
) {
    public static PublicVehicleResponse from(Vehicle vehicle) {
        return new PublicVehicleResponse(
                vehicle.getId(),
                vehicle.getLicensePlate(),
                vehicle.getVehicleType(),
                vehicle.getBodyType(),
                vehicle.getPayloadCapacity(),
                vehicle.getStatus());
    }
}
