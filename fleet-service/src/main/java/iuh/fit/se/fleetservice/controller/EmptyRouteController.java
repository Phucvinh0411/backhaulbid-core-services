package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.service.EmptyRouteService;
import iuh.fit.se.fleetservice.service.IdentityVerificationClient;
import iuh.fit.se.fleetservice.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/empty-routes")
@RequiredArgsConstructor
public class EmptyRouteController {

    private final EmptyRouteService emptyRouteService;
    private final IdentityVerificationClient identityVerificationClient;

    @GetMapping
    public List<EmptyRoute> listMine(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        requireCarrierOrAdmin(role);
        return emptyRouteService.listMine(carrierId.toString());
    }

    @PostMapping
    public ResponseEntity<EmptyRoute> declareEmptyRoute(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody EmptyRouteRequest request
    ) {
        requireCarrier(role);
        EmptyRoute emptyRoute = emptyRouteService.createEmptyRoute(request, carrierId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(emptyRoute);
    }

    @PostMapping("/optimal-matching")
    public ResponseEntity<MatchingService.MatchingResult> findOptimalMatching(
            @RequestHeader("X-User-Id") UUID callerId,
            @RequestHeader("X-User-Role") String role,
            @RequestBody List<MatchingService.Order> orders
    ) {
        requireCarrierOrAdmin(role);
        if ("CARRIER".equalsIgnoreCase(role)) {
            identityVerificationClient.assertVerified(callerId);
            return ResponseEntity.ok(emptyRouteService.findOptimalMatching(orders, callerId.toString()));
        }
        return ResponseEntity.ok(emptyRouteService.findOptimalMatching(orders, null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelRoute(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        requireCarrier(role);
        emptyRouteService.cancelEmptyRoute(id, carrierId.toString());
        return ResponseEntity.noContent().build();
    }

    private void requireCarrier(String role) {
        if (!"CARRIER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier role required");
        }
    }

    private void requireCarrierOrAdmin(String role) {
        if (!"CARRIER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
    }
}
