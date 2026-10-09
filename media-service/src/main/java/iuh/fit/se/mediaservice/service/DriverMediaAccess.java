package iuh.fit.se.mediaservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Media rules for a code-login driver session (gateway header X-Auth-Type = DRIVER_ASSIGNMENT): upload only
 * delivery proof images while the session can still write to its trip, and read only images attached to
 * that trip. Contract decides; any failure denies.
 */
@Component
public class DriverMediaAccess {
    public static final String DRIVER_ASSIGNMENT = "DRIVER_ASSIGNMENT";
    public static final String DRIVER_UPLOAD_FOLDER = "delivery-proofs";
    /** Pickup handover photos are the other image set a driver session may upload, only while it can write. */
    public static final Set<String> DRIVER_UPLOAD_FOLDERS = Set.of(DRIVER_UPLOAD_FOLDER, "handover-photos");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String contractUrl;
    private final String token;

    public DriverMediaAccess(ObjectMapper json, @Value("${app.contract.url:http://contract-service:8080}") String contractUrl,
                             @Value("${app.internal-token:}") String token) {
        this.json = json;
        this.contractUrl = contractUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public boolean canUpload(String sessionId, String folder) {
        if (!DRIVER_UPLOAD_FOLDERS.contains(folder)) return false;
        JsonNode status = get("/internal/driver-sessions/" + uuid(sessionId));
        return status != null && status.path("writable").asBoolean(false);
    }

    public boolean canRead(String sessionId, String key) {
        JsonNode result = get("/internal/driver-sessions/" + uuid(sessionId) + "/media?key="
                + URLEncoder.encode(key, StandardCharsets.UTF_8));
        return result != null && result.path("allowed").asBoolean(false);
    }

    private JsonNode get(String path) {
        if (token == null || token.isBlank() || path.contains("/null")) return null;
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(contractUrl + path))
                    .timeout(Duration.ofSeconds(5)).header("X-Internal-Token", token).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() == 200 ? json.readTree(response.body()) : null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException invalid) {
            return "null";
        }
    }
}
