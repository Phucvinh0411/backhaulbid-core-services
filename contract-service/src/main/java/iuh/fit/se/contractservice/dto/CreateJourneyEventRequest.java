package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJourneyEventRequest(
        @NotNull JourneyEventType eventType,
        TripStatus status,
        @Size(max = 500) String note,
        @Size(max = 500) String evidenceUrl
) {
}
