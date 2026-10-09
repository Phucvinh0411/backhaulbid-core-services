package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.service.FleetStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fleet/statistics")
@RequiredArgsConstructor
public class FleetStatisticsController {
    private final FleetStatisticsService statistics;

    @GetMapping("/me")
    public Map<String, Object> mine(@RequestHeader("X-User-Id") UUID carrierId, @RequestHeader("X-User-Role") String role) {
        return statistics.mine(carrierId, role);
    }

    @GetMapping("/admin")
    public Map<String, Object> admin(@RequestHeader("X-User-Role") String role) {
        return statistics.admin(role);
    }
}
