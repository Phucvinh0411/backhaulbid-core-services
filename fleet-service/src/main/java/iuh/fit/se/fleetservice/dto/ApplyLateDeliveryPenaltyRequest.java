package iuh.fit.se.fleetservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApplyLateDeliveryPenaltyRequest(
        @NotNull UUID tripId,
        @Min(1) @Max(3) int tier
) {
}
