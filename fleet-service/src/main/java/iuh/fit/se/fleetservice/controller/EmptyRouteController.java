package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.service.EmptyRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/empty-routes")
@RequiredArgsConstructor
public class EmptyRouteController {

    private final EmptyRouteService emptyRouteService;

    @PostMapping
    public ResponseEntity<EmptyRoute> declareEmptyRoute(@Valid @RequestBody EmptyRouteRequest request) {
        EmptyRoute emptyRoute = emptyRouteService.createEmptyRoute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(emptyRoute);
    }
}
