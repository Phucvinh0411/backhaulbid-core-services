package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateVehicleRequest(
        @NotBlank @Size(max = 30) String licensePlate,
        @NotNull VehicleType vehicleType,
        @Size(max = 100) String bodyType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal payloadCapacity,
        @Size(max = 500) String registrationUrl,
        @Size(max = 500) String inspectionUrl
) {
}
