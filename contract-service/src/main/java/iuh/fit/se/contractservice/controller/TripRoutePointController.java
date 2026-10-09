package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.dto.TripResponse;
import iuh.fit.se.contractservice.dto.UpdateTripRoutePointsRequest;
import iuh.fit.se.contractservice.service.TripRoutePointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/trips") @RequiredArgsConstructor
public class TripRoutePointController {
    private final TripRoutePointService service;

    @PatchMapping("/{tripId}/route-points")
    public TripResponse update(@RequestHeader("X-User-Id") UUID accountId,
                               @RequestHeader("X-User-Role") String role,
                               @PathVariable UUID tripId,
                               @Valid @RequestBody UpdateTripRoutePointsRequest request) {
        AccountRole parsedRole;
        try { parsedRole = AccountRole.valueOf(role); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role"); }
        return TripResponse.from(service.update(accountId, parsedRole, tripId, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("status", exception.getStatusCode().value(),
                "message", java.util.Objects.requireNonNullElse(exception.getReason(), "Route points could not be confirmed")));
    }
}
