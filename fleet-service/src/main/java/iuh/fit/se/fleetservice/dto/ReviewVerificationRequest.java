package iuh.fit.se.fleetservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewVerificationRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 500) String reason
) {
}
