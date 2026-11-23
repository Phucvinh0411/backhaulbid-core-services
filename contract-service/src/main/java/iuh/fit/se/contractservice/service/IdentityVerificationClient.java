package iuh.fit.se.contractservice.service;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Checks with identity-service whether an account has completed both eKYC and business verification.
 * Fails closed: any network/config/payload error refuses the contract operation.
 */
@Component
public class IdentityVerificationClient {

    public record SigningContact(
            String email,
            boolean emailVerified,
            String fullName,
            String companyName) {
        public SigningContact(String email, boolean emailVerified, String fullName) {
            this(email, emailVerified, fullName, null);
        }
    }

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
        ResponseEntity<Map> response = exchangeInternal(
                "/internal/accounts/{id}/verification-status",
                accountId);
        Map<?, ?> body = response.getBody();
        if (body == null || !(body.get("fullyVerified") instanceof Boolean fullyVerified)) {
            throw unavailable();
        }
        if (!fullyVerified) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản cần hoàn tất xác thực eKYC và xác thực doanh nghiệp trước khi ký hợp đồng");
        }
    }

    public SigningContact fetchSigningContact(UUID accountId) {
        ResponseEntity<Map> response = exchangeInternal(
                "/internal/accounts/{id}/signing-contact",
                accountId);
        Map<?, ?> body = response.getBody();
        if (body == null) {
            throw unavailable();
        }
        String email = body.get("email") instanceof String s && !s.isBlank() ? s.trim() : null;
        boolean emailVerified = Boolean.TRUE.equals(body.get("emailVerified"));
        String fullName = body.get("fullName") instanceof String s && !s.isBlank() ? s.trim() : null;
        String companyName = body.get("companyName") instanceof String s && !s.isBlank() ? s.trim() : null;
        return new SigningContact(email, emailVerified, fullName, companyName);
    }

    /** Best-effort lookup of a partner's company/display name for contract list enrichment. */
    public Optional<String> fetchPartnerName(UUID accountId) {
        if (accountId == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SigningContact contact = fetchSigningContact(accountId);
            String preferred = contact.companyName() != null ? contact.companyName() : contact.fullName();
            return Optional.ofNullable(preferred);
        } catch (ResponseStatusException | RestClientException ignored) {
            return Optional.empty();
        }
    }

    private ResponseEntity<Map> exchangeInternal(String path, UUID accountId) {
        if (token == null || token.isBlank()) {
            throw unavailable();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        try {
            return client.exchange(
                    baseUrl + path,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class,
                    accountId);
        } catch (RestClientException failure) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Không thể kiểm tra trạng thái xác thực tài khoản; vui lòng thử lại sau");
    }
}
