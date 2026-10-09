package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.service.TripHandoverService;
import iuh.fit.se.contractservice.service.driveraccess.DriverGrantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service-to-service driver access (identity, media and fleet). Not routed by the public gateway and
 * authenticated with the shared internal token. Requests are bounded and idempotent.
 */
@RestController
@RequestMapping("/internal")
public class InternalDriverAccessController {
    private final DriverGrantService grants;
    private final TripHandoverService handovers;
    private final String expectedToken;

    public InternalDriverAccessController(DriverGrantService grants, TripHandoverService handovers,
                                          @Value("${INTERNAL_SERVICE_TOKEN:}") String expectedToken) {
        this.grants = grants;
        this.handovers = handovers;
        this.expectedToken = expectedToken;
    }

    public record ExchangeRequest(@NotNull UUID grantId, @NotNull @Size(min = 43, max = 43) String secret, @NotNull UUID redeemRequestId) {
        @Override public String toString() { return "ExchangeRequest(" + grantId + ", <secret redacted>, " + redeemRequestId + ")"; }
    }

    public record RevokeRequest(@Size(max = 40) String reason) {
    }

    @PostMapping("/driver-grants/exchange")
    public ResponseEntity<DriverGrantService.Exchange> exchange(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                                @Valid @RequestBody ExchangeRequest request) {
        authorize(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(grants.exchange(request.grantId(), request.secret(), request.redeemRequestId()));
    }

    @GetMapping("/driver-sessions/{sessionId}")
    public ResponseEntity<DriverGrantService.SessionStatus> status(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                                   @PathVariable UUID sessionId) {
        authorize(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grants.status(sessionId));
    }

    @PostMapping("/driver-sessions/{sessionId}/revoke")
    public ResponseEntity<Void> revoke(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                       @PathVariable UUID sessionId, @RequestBody(required = false) RevokeRequest request) {
        authorize(token);
        grants.revokeSession(sessionId, request == null || request.reason() == null ? "LOGOUT" : request.reason());
        return ResponseEntity.noContent().build();
    }

    /** Called by fleet when a driver profile is deleted or leaves VERIFIED. Idempotent. */
    @PostMapping("/driver-profiles/{profileId}/revoke")
    public ResponseEntity<Map<String, Integer>> revokeProfile(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                              @PathVariable UUID profileId, @RequestBody(required = false) RevokeRequest request) {
        authorize(token);
        String reason = request == null || request.reason() == null ? "PROFILE_INACTIVE" : request.reason();
        return ResponseEntity.ok(Map.of("revokedTrips", grants.revokeProfile(profileId, reason)));
    }

    @GetMapping("/driver-sessions/{sessionId}/media")
    public ResponseEntity<Map<String, Boolean>> media(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                      @PathVariable UUID sessionId, @RequestParam("key") String key) {
        authorize(token);
        // Proofs and evidence come from the grant check; handover photos from the handover of the same trip.
        boolean allowed = grants.canReadMedia(sessionId, key) || handovers.canReadPhoto(sessionId, key);
        return ResponseEntity.ok(Map.of("allowed", allowed));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Request could not be completed")));
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
