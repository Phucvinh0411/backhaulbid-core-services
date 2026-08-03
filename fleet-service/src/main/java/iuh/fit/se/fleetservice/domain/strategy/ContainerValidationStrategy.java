package iuh.fit.se.fleetservice.domain.strategy;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;

import java.math.BigDecimal;

public final class ContainerValidationStrategy implements VehicleValidationStrategy {
    @Override
    public boolean validate(Vehicle vehicle, BigDecimal requiredPayload, VehicleType requiredType) {
        return requiredType == VehicleType.CONTAINER_TRACTOR
                && vehicle.getStatus() == VehicleStatus.VERIFIED
                && vehicle.getVehicleType() == VehicleType.CONTAINER_TRACTOR
                && vehicle.getPayloadCapacity() != null
                && requiredPayload != null
                && vehicle.getPayloadCapacity().compareTo(requiredPayload) >= 0;
    }
}
