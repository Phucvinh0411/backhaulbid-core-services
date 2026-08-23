package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TrackingLog;
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
    public static TrackingResponse from(TrackingLog trackingLog) {
        return new TrackingResponse(
                trackingLog.getId(),
                trackingLog.getLatitude(),
                trackingLog.getLongitude(),
                trackingLog.getStatus(),
                trackingLog.getRecordedAt()
        );
    }
}
