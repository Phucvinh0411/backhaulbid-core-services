package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripMilestone;
import iuh.fit.se.contractservice.domain.enums.MilestoneStatus;

import java.time.Instant;
import java.util.UUID;

public record MilestoneResponse(
        UUID id,
        UUID tripId,
        String milestoneName,
        Double targetLat,
        Double targetLng,
        Double actualLat,
        Double actualLng,
        MilestoneStatus status,
        Instant reachedAt,
        Integer sequenceOrder,
        Instant createdAt
) {
    public static MilestoneResponse from(TripMilestone m) {
        return new MilestoneResponse(
                m.getId(),
                m.getTrip().getId(),
                m.getMilestoneName(),
                m.getTargetLat(),
                m.getTargetLng(),
                m.getActualLat(),
                m.getActualLng(),
                m.getStatus(),
                m.getReachedAt(),
                m.getSequenceOrder(),
                m.getCreatedAt()
        );
    }
}
