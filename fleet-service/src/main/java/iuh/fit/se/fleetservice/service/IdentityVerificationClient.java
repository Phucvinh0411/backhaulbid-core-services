package iuh.fit.se.fleetservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Checks with identity-service whether an account has completed both eKYC and business verification.
 * Fails closed: any network/config/payload error refuses the operation.
 */
@Component
public class IdentityVerificationClient {

    private final RestTemplate client;
    private final String baseUrl;
    private final String token;

    public IdentityVerificationClient(
            RestTemplateBuilder builder,
            @Value("${IDENTITY_SERVICE_URL:http://identity-service:8080}") String baseUrl,
            @Value("${INTERNAL_SERVICE_TOKEN:}") String token) {
        this.client = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public void assertVerified(UUID accountId) {
        if (token == null || token.isBlank()) {
            throw unavailable();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        ResponseEntity<Map> response;
        try {
            response = client.exchange(
                    baseUrl + "/internal/accounts/{id}/verification-status",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class,
                    accountId);
        } catch (RestClientException failure) {
            throw unavailable();
        }

        Map<?, ?> body = response.getBody();
        if (body == null || !(body.get("fullyVerified") instanceof Boolean fullyVerified)) {
            throw unavailable();
        }
        if (!fullyVerified) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản cần hoàn tất xác thực eKYC và xác thực doanh nghiệp trước khi thực hiện thao tác này");
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Không thể kiểm tra trạng thái xác thực tài khoản; vui lòng thử lại sau");
    }
}
