package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
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
        Instant recordedAt
) {
    public static TripLocationResponse from(TripLocationUpdate location) {
        return new TripLocationResponse(location.getId(), location.getTrip().getId(), location.getActorId(),
                location.getLatitude(), location.getLongitude(), location.getLabel(), location.getSource(),
                location.getStatus(), location.getRecordedAt());
    }
}
