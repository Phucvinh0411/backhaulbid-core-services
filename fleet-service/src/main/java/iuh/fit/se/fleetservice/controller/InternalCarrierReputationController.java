package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.dto.ApplyLateDeliveryPenaltyRequest;
import iuh.fit.se.fleetservice.dto.CarrierReputationResponse;
import iuh.fit.se.fleetservice.service.CarrierReputationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/carrier-reputations")
@RequiredArgsConstructor
public class InternalCarrierReputationController {
    private final CarrierReputationService reputationService;

    @Value("${INTERNAL_SERVICE_TOKEN:}")
    private String expectedToken;

    @GetMapping("/{carrierId}")
    public CarrierReputationResponse get(
            @PathVariable UUID carrierId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        authorize(token);
        return reputationService.getScore(carrierId);
    }

    @PostMapping("/{carrierId}/late-delivery-penalties")
    public CarrierReputationResponse applyLateDeliveryPenalty(
            @PathVariable UUID carrierId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody ApplyLateDeliveryPenaltyRequest request
    ) {
        authorize(token);
        return reputationService.applyLateDeliveryTier(carrierId, request.tripId(), request.tier());
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
