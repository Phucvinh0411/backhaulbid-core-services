package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripLocationSource;
import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.time.Instant;
import java.util.UUID;

public record TripLocationResponse(
        UUID id,
        UUID tripId,
        UUID actorId,
        Double latitude,
        Double longitude,
        String label,
        TripLocationSource source,
        TripStatus status,
        Instant recordedAt,
        Instant capturedAt,
        Double accuracyMeters
) {
}
