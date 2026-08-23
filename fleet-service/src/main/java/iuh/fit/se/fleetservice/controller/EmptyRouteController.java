package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.service.EmptyRouteService;
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
