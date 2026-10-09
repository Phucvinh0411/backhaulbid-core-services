package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.statistics.StatisticsRange;
import iuh.fit.se.contractservice.statistics.TripStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/statistics")
@RequiredArgsConstructor
public class TripStatisticsController {
    private final TripStatisticsService statistics;

    @GetMapping("/me")
    public Map<String, Object> mine(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String bucket
    ) {
        String normalized = role.toUpperCase();
        if (!normalized.equals("SHIPPER") && !normalized.equals("CARRIER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Statistics are available to shippers and carriers");
        return statistics.owner(accountId, normalized, StatisticsRange.parse(dateFrom, dateTo, bucket));
    }

    @GetMapping("/admin")
    public Map<String, Object> admin(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String bucket
    ) {
        if (!role.equalsIgnoreCase("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required");
        return statistics.admin(StatisticsRange.parse(dateFrom, dateTo, bucket));
    }
}
