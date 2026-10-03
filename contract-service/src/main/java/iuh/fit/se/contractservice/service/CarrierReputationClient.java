package iuh.fit.se.contractservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CarrierReputationClient {
    @Value("${FLEET_SERVICE_URL:http://fleet-service:8080}")
    private String fleetServiceUrl;

    @Value("${INTERNAL_SERVICE_TOKEN:}")
    private String token;

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    public void deductLateDeliveryPoints(UUID carrierId, UUID tripId, int tier) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", token);
        Map<String, Object> body = Map.of("tripId", tripId.toString(), "tier", tier);
        restTemplate.exchange(fleetServiceUrl.replaceAll("/$", "") + "/internal/carrier-reputations/"
                        + carrierId + "/late-delivery-penalties",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
