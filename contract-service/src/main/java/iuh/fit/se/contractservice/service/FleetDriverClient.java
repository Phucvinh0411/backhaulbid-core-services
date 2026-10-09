package iuh.fit.se.contractservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/** Uses fleet's carrier-scoped read; no profile ownership is accepted from the caller. */
@Component
public class FleetDriverClient {
    private final RestTemplate restTemplate;
    private final String fleetServiceUrl;

    public FleetDriverClient(RestTemplateBuilder builder,
            @Value("${FLEET_SERVICE_URL:http://fleet-service:8080}") String fleetServiceUrl) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5)).build();
        this.fleetServiceUrl = fleetServiceUrl.replaceAll("/+$", "");
    }

    public void requireAssignableDriver(UUID tripCarrierId, UUID driverId) {
        if (driverId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver profile is required");
        }
        HttpHeaders headers = new HttpHeaders();
        // Fleet's existing public carrier read permits CARRIER and returns only this carrier's profiles.
        headers.set("X-User-Role", "CARRIER");
        headers.set("X-User-Id", tripCarrierId.toString());
        DriverProfile[] profiles;
        try {
            var uri = UriComponentsBuilder.fromHttpUrl(fleetServiceUrl)
                    .path("/api/v1/drivers/carrier/{carrierId}").buildAndExpand(tripCarrierId).toUri();
            profiles = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), DriverProfile[].class).getBody();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot verify driver fleet membership; try again later");
        }
        if (profiles == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot verify driver fleet membership; try again later");
        }
        DriverProfile driver = Arrays.stream(profiles).filter(profile -> profile != null && driverId.equals(profile.id()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Driver must belong to the trip carrier's fleet"));
        if (!"VERIFIED".equals(driver.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver profile must be verified before assignment");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriverProfile(UUID id, String status) {}
}
