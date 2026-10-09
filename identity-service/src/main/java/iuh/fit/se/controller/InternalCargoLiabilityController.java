package iuh.fit.se.controller;

import iuh.fit.se.dto.CargoLiabilityDtos;
import iuh.fit.se.service.CargoLiabilityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Service-to-service eligibility check for bidding. The gateway does not route it, and the shared internal token guards
 * it. The security chain lets it through so that this check decides.
 */
@RestController
@RequestMapping("/internal/cargo-liability")
public class InternalCargoLiabilityController {

    private final CargoLiabilityService cargoLiabilityService;
    private final String expectedToken;

    public InternalCargoLiabilityController(CargoLiabilityService cargoLiabilityService,
                                            @Value("${app.internal-token:}") String expectedToken) {
        this.cargoLiabilityService = cargoLiabilityService;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/accounts/{accountId}/eligibility")
    public CargoLiabilityDtos.Eligibility eligibility(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        authorize(token);
        return new CargoLiabilityDtos.Eligibility(cargoLiabilityService.isEligible(accountId));
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
