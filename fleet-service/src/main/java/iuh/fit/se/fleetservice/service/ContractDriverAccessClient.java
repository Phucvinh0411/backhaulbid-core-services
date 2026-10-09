package iuh.fit.se.fleetservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Synchronous revocation: fleet must not report success if trip access could not be removed. */
@Component
public class ContractDriverAccessClient {
    private final RestTemplate client;
    private final String baseUrl;
    private final String token;

    public ContractDriverAccessClient(RestTemplateBuilder builder,
            @Value("${CONTRACT_SERVICE_URL:http://contract-service:8080}") String baseUrl,
            @Value("${INTERNAL_SERVICE_TOKEN:}") String token) {
        this.client = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(5)).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public void revokeProfile(UUID profileId, String reason) {
        if (token == null || token.isBlank()) throw unavailable();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        try {
            client.exchange(baseUrl + "/internal/driver-profiles/{id}/revoke", HttpMethod.POST,
                    new HttpEntity<>(Map.of("reason", reason), headers), Void.class, profileId);
        } catch (RestClientException failure) {
            // Do not expose the internal URL, credential or downstream body in an owner error.
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Cannot revoke driver trip access; try again later");
    }
}
