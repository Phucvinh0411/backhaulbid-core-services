package iuh.fit.se.contractservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Reads one private object (claim evidence or a handover photo) from the media service over the internal network,
 * with the shared token. The caller must already have checked access to the trip or claim. Any failure returns
 * empty, never a partial file.
 */
@Component
public class MediaEvidenceClient {
    /** Largest evidence file accepted for proxying; larger objects are refused rather than buffered. */
    static final int MAX_BYTES = 15 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String mediaUrl;
    private final String token;

    public MediaEvidenceClient(@Value("${MEDIA_SERVICE_URL:http://media-service:8080}") String mediaUrl,
                               @Value("${INTERNAL_SERVICE_TOKEN:}") String token) {
        this.mediaUrl = mediaUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
    }

    public record Fetched(byte[] bytes, String contentType) {
    }

    public Optional<Fetched> fetch(String objectKey) {
        if (token.isBlank()) return Optional.empty();
        HttpRequest request = HttpRequest.newBuilder(URI.create(mediaUrl + "/internal/media/objects?key="
                        + URLEncoder.encode(objectKey, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(20))
                .header("X-Internal-Token", token)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0 || response.body().length > MAX_BYTES) {
                return Optional.empty();
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return Optional.of(new Fetched(response.body(), contentType));
        } catch (IOException unavailable) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
