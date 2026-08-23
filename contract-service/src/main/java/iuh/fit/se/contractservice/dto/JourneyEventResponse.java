package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
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
    public static JourneyEventResponse from(JourneyEvent event) {
        return new JourneyEventResponse(
                event.getId(), event.getTrip().getId(), event.getActorId(), event.getEventType(),
                event.getStatus(), event.getNote(), event.getEvidenceUrl(), event.getRecordedAt());
    }
}
