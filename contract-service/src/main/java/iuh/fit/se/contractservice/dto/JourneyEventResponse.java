package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.time.Instant;
import java.util.UUID;

public record JourneyEventResponse(
        UUID id,
        UUID tripId,
        UUID actorId,
        JourneyEventType eventType,
        TripStatus status,
        String note,
        String evidenceUrl,
        Instant recordedAt
) {
}
