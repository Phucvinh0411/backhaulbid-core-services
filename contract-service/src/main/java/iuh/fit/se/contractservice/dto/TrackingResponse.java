package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.time.Instant;
import java.util.UUID;

public record TrackingResponse(
        UUID id,
        Double latitude,
        Double longitude,
        TripStatus status,
        Instant recordedAt
) {
}
