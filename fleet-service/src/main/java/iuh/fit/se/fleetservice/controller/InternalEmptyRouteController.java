package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.service.EmptyRouteService;
import iuh.fit.se.fleetservice.service.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/empty-routes")
@RequiredArgsConstructor
public class InternalEmptyRouteController {

    private final EmptyRouteService emptyRouteService;

    @Value("${app.internal-token:${INTERNAL_SERVICE_TOKEN:}}")
    private String expectedToken;

    @PostMapping("/match")
    public ResponseEntity<List<EmptyRoute>> findMatchingRoutes(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> request
    ) {
        authorize(token);
        String origin = (String) request.get("origin");
        String destination = (String) request.get("destination");

        List<EmptyRoute> matches = emptyRouteService.findMatchingRoutes(origin, destination, request);
        return ResponseEntity.ok(matches);
    }

    @PostMapping("/optimal-matching")
    public ResponseEntity<MatchingService.MatchingResult> findOptimalMatching(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody List<MatchingService.Order> orders
    ) {
        authorize(token);
        return ResponseEntity.ok(emptyRouteService.findOptimalMatching(orders));
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null
                || !MessageDigest.isEqual(
                        expectedToken.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
