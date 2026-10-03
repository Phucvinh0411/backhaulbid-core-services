package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.dto.CarrierReputationHistoryResponse;
import iuh.fit.se.fleetservice.dto.CarrierReputationResponse;
import iuh.fit.se.fleetservice.service.CarrierReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fleet/reputation")
@RequiredArgsConstructor
public class CarrierReputationController {
    private final CarrierReputationService reputationService;

    @GetMapping("/mine")
    public CarrierReputationHistoryResponse mine(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        if (!"CARRIER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier role required");
        }
        return reputationService.getHistory(carrierId);
    }

    @GetMapping("/carrier/{carrierId}")
    public CarrierReputationResponse carrier(
            @PathVariable UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        if (!"SHIPPER".equalsIgnoreCase(role)
                && !"CARRIER".equalsIgnoreCase(role)
                && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported account role");
        }
        return reputationService.getScore(carrierId);
    }

    @GetMapping("/carrier/{carrierId}/history")
    public CarrierReputationHistoryResponse carrierHistory(
            @PathVariable UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        if (!"SHIPPER".equalsIgnoreCase(role)
                && !"CARRIER".equalsIgnoreCase(role)
                && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported account role");
        }
        return reputationService.getHistory(carrierId);
    }
}
