package iuh.fit.se.service.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Identity -> contract internal calls for driver sessions. Contract owns the assignment; identity never
 * decides access on its own. Any transport failure fails closed (503).
 */
@Component
public class ContractDriverClient {
    public record Exchange(UUID sessionId, UUID tripId, UUID driverProfileId, UUID grantId, long assignmentVersion, boolean replay) {
    }

    public record Status(UUID sessionId, UUID tripId, UUID driverProfileId, UUID grantId, long assignmentVersion,
                         boolean readable, boolean writable) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;

    public ContractDriverClient(ObjectMapper json, @Value("${app.contract.url:http://contract-service:8080}") String baseUrl,
                                @Value("${app.internal-token:}") String token) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public Exchange exchange(UUID grantId, String secret, UUID requestId) {
        JsonNode body = send("POST", "/internal/driver-grants/exchange",
                Map.of("grantId", grantId.toString(), "secret", secret, "redeemRequestId", requestId.toString()));
        return new Exchange(uuid(body, "sessionId"), uuid(body, "tripId"), uuid(body, "driverProfileId"), uuid(body, "grantId"),
                body.path("assignmentVersion").asLong(), body.path("replay").asBoolean());
    }

    public Status status(UUID sessionId) {
        JsonNode body = send("GET", "/internal/driver-sessions/" + sessionId, null);
        return new Status(uuid(body, "sessionId"), uuid(body, "tripId"), uuid(body, "driverProfileId"), uuid(body, "grantId"),
                body.path("assignmentVersion").asLong(), body.path("readable").asBoolean(), body.path("writable").asBoolean());
    }

    public void revoke(UUID sessionId, String reason) {
        send("POST", "/internal/driver-sessions/" + sessionId + "/revoke", Map.of("reason", reason));
    }

    private JsonNode send(String method, String path, Object body) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Driver access is not configured");
        try {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5))
                    .header("X-Internal-Token", token).header("Accept", "application/json");
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 204) return json.createObjectNode();
            JsonNode node = response.body().length == 0 ? json.createObjectNode() : json.readTree(response.body());
            if (status >= 200 && status < 300) return node;
            if (status == 401 || status == 404 || status == 409 || status == 410 || status == 400) {
                // Contract's message is already user-facing and reveals nothing about other trips.
                throw new ResponseStatusException(HttpStatus.valueOf(status == 404 ? 401 : status),
                        node.path("message").asText("Mã nhận chuyến không đúng."));
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Driver access check is unavailable");
        } catch (ResponseStatusException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Driver access check is unavailable");
        } catch (Exception unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Driver access check is unavailable");
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asText());
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Driver access response is invalid");
        }
    }
}
