package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.config.RabbitMQConfig;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import iuh.fit.se.contractservice.dto.TripLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripLocationEventPublisher {

    private static final Duration THROTTLE_WINDOW = Duration.ofSeconds(3);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final Map<UUID, Instant> lastPublishedByTrip = new ConcurrentHashMap<>();

    public void publishLatestLocation(Trip trip, TripLocationUpdate previous, TripLocationUpdate latest) {
        if (trip == null || latest == null) {
            return;
        }
        UUID tripId = trip.getId();
        Instant now = clock.instant();
        Instant lastPublished = lastPublishedByTrip.get(tripId);
        if (lastPublished != null && Duration.between(lastPublished, now).compareTo(THROTTLE_WINDOW) < 0) {
            return;
        }
        lastPublishedByTrip.put(tripId, now);

        try {
            Double heading = null;
            Double speedKmh = null;
            if (previous != null && previous.getLatitude() != null && previous.getLongitude() != null
                    && latest.getLatitude() != null && latest.getLongitude() != null) {
                double distMeters = distanceMeters(
                        previous.getLatitude(), previous.getLongitude(),
                        latest.getLatitude(), latest.getLongitude());
                if (distMeters >= 3.0) {
                    heading = bearingDegrees(
                            previous.getLatitude(), previous.getLongitude(),
                            latest.getLatitude(), latest.getLongitude());
                }
                Instant t1 = TripTrackingService.captureTime(previous);
                Instant t2 = TripTrackingService.captureTime(latest);
                if (t1 != null && t2 != null && t2.isAfter(t1)) {
                    double seconds = Duration.between(t1, t2).toMillis() / 1000.0;
                    if (seconds > 0.5 && seconds <= 300) {
                        double calculatedKmh = (distMeters / seconds) * 3.6;
                        if (calculatedKmh <= 160.0) {
                            speedKmh = Math.round(calculatedKmh * 10.0) / 10.0;
                        }
                    }
                }
            }

            TripLocationResponse pointResponse = TripLocationResponse.from(latest);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tripId", tripId.toString());
            data.put("shipperId", trip.getShipperId() != null ? trip.getShipperId().toString() : null);
            data.put("carrierId", trip.getCarrierId() != null ? trip.getCarrierId().toString() : null);
            data.put("status", trip.getStatus() != null ? trip.getStatus().name() : null);
            data.put("point", Map.of(
                    "id", pointResponse.id() != null ? pointResponse.id().toString() : UUID.randomUUID().toString(),
                    "tripId", tripId.toString(),
                    "latitude", pointResponse.latitude(),
                    "longitude", pointResponse.longitude(),
                    "accuracyMeters", pointResponse.accuracyMeters() != null ? pointResponse.accuracyMeters() : 0.0,
                    "source", pointResponse.source() != null ? pointResponse.source().name() : "GPS",
                    "capturedAt", pointResponse.capturedAt() != null ? pointResponse.capturedAt().toString() : now.toString(),
                    "recordedAt", pointResponse.recordedAt() != null ? pointResponse.recordedAt().toString() : now.toString()
            ));
            if (heading != null) {
                data.put("heading", heading);
            }
            if (speedKmh != null) {
                data.put("speedKmh", speedKmh);
            }

            Map<String, Object> envelope = new LinkedHashMap<>(data);
            envelope.put("pattern", RabbitMQConfig.LOCATION_ROUTING);
            envelope.put("data", data);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CONTRACT_EXCHANGE,
                    RabbitMQConfig.LOCATION_ROUTING,
                    envelope
            );
        } catch (Exception ex) {
            log.warn("[TripTracking] Failed to publish location update for trip {}: {}", tripId, ex.getMessage());
        }
    }

    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_METERS * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
    }

    static double bearingDegrees(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        double theta = Math.toDegrees(Math.atan2(y, x));
        return Math.round(((theta + 360.0) % 360.0) * 10.0) / 10.0;
    }
}

