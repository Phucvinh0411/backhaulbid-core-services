package iuh.fit.se.fleetservice.domain.strategy;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;

import java.math.BigDecimal;

public interface VehicleValidationStrategy {
    boolean validate(Vehicle vehicle, BigDecimal requiredPayload, VehicleType requiredType);
}
