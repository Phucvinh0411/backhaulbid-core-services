package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMilestoneRequest(
        @NotBlank(message = "milestoneName is required")
        @Size(max = 255)
        String milestoneName,

        @NotNull(message = "targetLat is required")
        Double targetLat,

        @NotNull(message = "targetLng is required")
        Double targetLng,

        @NotNull(message = "sequenceOrder is required")
        @Positive(message = "sequenceOrder must be positive")
        Integer sequenceOrder
) {}
