package iuh.fit.se.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Verifies Google Identity Services ID tokens against Google's tokeninfo endpoint and checks
 * issuer, audience (app.google.client-id), verified email flag, and expiration.
 */
@Component
public class HttpGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final String DEFAULT_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String tokenInfoUrl;
    private final Clock clock;

    @Autowired
    public HttpGoogleTokenVerifier(
            ObjectMapper objectMapper,
            @Value("${app.google.client-id:}") String clientId) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                objectMapper,
                clientId,
                DEFAULT_TOKENINFO_URL,
                Clock.systemUTC());
    }

    HttpGoogleTokenVerifier(
            HttpClient http,
            ObjectMapper objectMapper,
            String clientId,
            String tokenInfoUrl,
            Clock clock) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.tokenInfoUrl = tokenInfoUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        if (clientId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Đăng nhập Google chưa được cấu hình trên máy chủ");
        }
        if (idToken == null || idToken.isBlank()) {
            throw invalidToken();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(tokenInfoUrl + "?id_token=" + URLEncoder.encode(idToken.trim(), StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 400 || response.statusCode() == 401) {
                throw invalidToken();
            }
            if (response.statusCode() != 200) {
                throw googleUnavailable();
            }
            JsonNode payload = objectMapper.readTree(response.body());
            return parseAndValidate(payload);
        } catch (ResponseStatusException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw googleUnavailable();
        } catch (Exception failure) {
            throw googleUnavailable();
        }
    }

    private GoogleUserInfo parseAndValidate(JsonNode payload) {
        String audience = textOrNull(payload.path("aud"));
        String issuer = textOrNull(payload.path("iss"));
        String sub = textOrNull(payload.path("sub"));
        String email = textOrNull(payload.path("email"));
        String emailVerified = textOrNull(payload.path("email_verified"));
        long exp = payload.path("exp").asLong(0L);
        if (exp == 0L && payload.hasNonNull("exp")) {
            try {
                exp = Long.parseLong(payload.path("exp").asText("0").trim());
            } catch (NumberFormatException ignored) {
                throw invalidToken();
            }
        }

        if (!clientId.equals(audience)) {
            throw invalidToken();
        }
        if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) {
            throw invalidToken();
        }
        if (sub == null || email == null || !"true".equalsIgnoreCase(emailVerified)) {
            throw invalidToken();
        }
        if (exp <= clock.instant().getEpochSecond()) {
            throw invalidToken();
        }

        String fullName = textOrNull(payload.path("name"));
        String picture = sanitizeAvatarUrl(textOrNull(payload.path("picture")));
        return new GoogleUserInfo(sub, email.toLowerCase(Locale.ROOT), fullName, picture);
    }

    private static String sanitizeAvatarUrl(String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return null;
        }
        return url.length() > 500 ? url.substring(0, 500) : url;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token Google không hợp lệ hoặc đã hết hạn");
    }

    private static ResponseStatusException googleUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể kết nối Google để xác thực");
    }
}
