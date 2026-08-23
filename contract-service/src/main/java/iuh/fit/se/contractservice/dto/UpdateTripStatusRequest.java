package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.TripStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTripStatusRequest(
        @NotNull TripStatus status,
        String cancellationReason
) {
}
