package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.entity.CarrierReputationEntry;

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
    public static CarrierReputationEntryResponse from(CarrierReputationEntry entry) {
        return new CarrierReputationEntryResponse(
                entry.getTripId(),
                entry.getTier(),
                entry.getPointsDelta(),
                entry.getScoreAfter(),
                entry.getReason(),
                entry.getCreatedAt()
        );
    }
}
