package iuh.fit.se.fleetservice.dto;

import java.time.Instant;
import java.util.UUID;

public record CarrierReputationEntryResponse(
        UUID tripId,
        int tier,
        int pointsDelta,
        int scoreAfter,
        String reason,
        Instant createdAt
) {
}
