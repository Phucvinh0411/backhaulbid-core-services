package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.service.EmptyRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/empty-routes")
@RequiredArgsConstructor
public class InternalEmptyRouteController {

    private final EmptyRouteService emptyRouteService;

    @PostMapping("/match")
    public ResponseEntity<List<EmptyRoute>> findMatchingRoutes(@RequestBody Map<String, Object> request) {
        String origin = (String) request.get("origin");
        String destination = (String) request.get("destination");
        
        List<EmptyRoute> matches = emptyRouteService.findMatchingRoutes(origin, destination, request);
        return ResponseEntity.ok(matches);
    }
}
