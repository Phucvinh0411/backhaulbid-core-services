package iuh.fit.se.controller;

import iuh.fit.se.service.driver.DriverSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Driver sign-in with a "mã nhận chuyến". Tokens are returned in the body only (the app keeps them). */
@RestController
@RequestMapping("/api/v1/auth/driver")
@RequiredArgsConstructor
public class DriverAuthController {
    private final DriverSessionService drivers;

    public record RedeemRequest(@NotBlank @Size(max = 100) String code, @NotNull UUID redeemRequestId) {
        @Override public String toString() { return "RedeemRequest(<code redacted>, " + redeemRequestId + ")"; }
    }

    public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {
        @Override public String toString() { return "RefreshRequest(<redacted>)"; }
    }

    @PostMapping("/redeem")
    public ResponseEntity<DriverSessionService.DriverTokens> redeem(@Valid @RequestBody RedeemRequest body, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(drivers.redeem(body.code(), body.redeemRequestId(), clientKey(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<DriverSessionService.DriverTokens> refresh(@Valid @RequestBody RefreshRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(drivers.refresh(body.refreshToken()));
    }

    /** The gateway only lets an authenticated driver-session token reach this; the session revokes itself. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-User-Id", required = false) String sessionId,
                                       @RequestHeader(value = "X-Auth-Type", required = false) String authType) {
        if (!"DRIVER_ASSIGNMENT".equals(authType) || sessionId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver session required");
        }
        drivers.logout(UUID.fromString(sessionId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Request could not be completed")));
    }

    /** The gateway appends the caller's address last; earlier X-Forwarded-For entries can be forged. */
    static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
