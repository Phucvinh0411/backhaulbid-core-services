package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.dto.TripRoutePoint;
import iuh.fit.se.contractservice.service.TripRoadRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Road preview between two points, used by the empty-route form. It reads the same OSRM-compatible provider as
 * tracking. The lookup never blocks: PENDING means the road fetch is running and the caller should ask again.
 */
@RestController
@RequestMapping("/api/v1/trips/road-preview")
@RequiredArgsConstructor
public class RoadPreviewController {

    private final TripRoadRouteService roads;

    /** {@code geometry} is a GeoJSON LineString ([lng, lat] pairs) when {@code status} is READY, otherwise null. */
    public record RoadPreviewResponse(String status, Map<String, Object> geometry, Long distanceMeters) {}

    @GetMapping
    public ResponseEntity<RoadPreviewResponse> preview(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam double fromLat, @RequestParam double fromLng,
            @RequestParam double toLat, @RequestParam double toLng) {
        requirePlanner(role);
        var result = roads.lookup(point(fromLat, fromLng), point(toLat, toLng));
        if (result == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tọa độ không hợp lệ");
        Object distance = result.meta() == null ? null : result.meta().get("distanceMeters");
        Long distanceMeters = distance instanceof Number number ? number.longValue() : null;
        return ResponseEntity.ok(new RoadPreviewResponse(result.status().name(), result.geometry(), distanceMeters));
    }

    private static void requirePlanner(String role) {
        AccountRole parsed;
        try {
            parsed = AccountRole.valueOf(role);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role");
        }
        if (parsed != AccountRole.CARRIER && parsed != AccountRole.SHIPPER && parsed != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role cannot preview routes");
        }
    }

    private static TripRoutePoint point(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tọa độ không hợp lệ");
        }
        return new TripRoutePoint(latitude, longitude, null, null, null);
    }
}
