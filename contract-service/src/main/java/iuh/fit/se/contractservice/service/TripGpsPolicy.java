package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.dto.GpsBatchRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Device locations describe operational observations; they cannot authorize delivery or identity. */
public final class TripGpsPolicy {
    private TripGpsPolicy() {}
    public static boolean validate(GpsBatchRequest.Point point, Instant now) {
        if (point == null || point.sampleId() == null || point.capturedAt() == null
                || !valid(point.latitude(), -90, 90) || !valid(point.longitude(), -180, 180)
                || !valid(point.accuracyMeters(), 0, 10000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid GPS sample");
        }
        if (point.capturedAt().isAfter(now.plusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPS timestamp is in the future");
        }
        return point.accuracyMeters() <= 100 && !point.capturedAt().isBefore(now.minusSeconds(1800));
    }
    private static boolean valid(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }
    public static String freshness(Instant capturedAt, boolean stopped, Instant now) {
        if (stopped) return "STOPPED";
        if (capturedAt == null) return "NONE";
        long seconds = Duration.between(capturedAt, now).getSeconds();
        return seconds <= 120 ? "FRESH" : seconds <= 600 ? "STALE" : "LOST";
    }
}
