package iuh.fit.se.controller;

import iuh.fit.se.service.EkycService;
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
import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service eKYC verification check for bidding-service.
 * Protected by the shared X-Internal-Token header.
 */
@RestController
@RequestMapping("/internal/ekyc-verifications")
public class InternalEkycVerificationController {

    private final EkycService ekycService;
    private final String expectedToken;

    public InternalEkycVerificationController(
            EkycService ekycService,
            @Value("${app.internal-token:}") String expectedToken) {
        this.ekycService = ekycService;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/accounts/{accountId}/eligibility")
    public Map<String, Object> eligibility(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        authorize(token);
        boolean verified = ekycService.isVerified(accountId);
        return Map.of("eligible", verified, "verified", verified);
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}

