package iuh.fit.se.fleetservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Fetches private vehicle and driver documents from media-service via its token-protected
 * internal endpoint after ownership or admin authorization is verified in fleet-service.
 */
@Component
public class FleetMediaClient {

    public record DocumentContent(byte[] bytes, String contentType) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String baseUrl;
    private final String token;

    public FleetMediaClient(
            @Value("${app.media.url:http://media-service:8080}") String baseUrl,
            @Value("${app.internal-token:}") String token
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public DocumentContent fetch(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa có tài liệu đính kèm");
        }
        if (token == null || token.isBlank()) {
            throw unavailable();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            baseUrl + "/internal/media/objects?key=" + URLEncoder.encode(keyOrUrl, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Internal-Token", token)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài liệu không còn trong kho lưu trữ");
            }
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            return new DocumentContent(response.body(), contentTypeOf(keyOrUrl));
        } catch (ResponseStatusException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    static String contentTypeOf(String keyOrUrl) {
        String lower = keyOrUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    public static ResponseEntity<byte[]> toInlineResponse(DocumentContent doc) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(doc.bytes());
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is unavailable");
    }
}
