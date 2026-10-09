package iuh.fit.se.service;

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
import java.time.Duration;
import java.util.Locale;

/**
 * Reads a private certificate file from media-service over its token-protected internal endpoint. The caller must
 * already have checked that the requester is an admin. Any transport or storage failure fails closed (503).
 */
@Component
public class MediaCertificateClient {

    public record Certificate(byte[] bytes, String contentType) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String baseUrl;
    private final String token;

    public MediaCertificateClient(@Value("${app.media.url:http://media-service:8080}") String baseUrl,
                                  @Value("${app.internal-token:}") String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public Certificate fetch(String key) {
        if (token == null || token.isBlank()) throw unavailable();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            baseUrl + "/internal/media/objects?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Internal-Token", token)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chứng từ không còn trong kho lưu trữ");
            }
            if (response.statusCode() != 200) throw unavailable();
            return new Certificate(response.body(), contentTypeOf(key));
        } catch (ResponseStatusException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    /** The type comes from the stored key's extension, never from what storage reports. */
    static String contentTypeOf(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Certificate storage is unavailable");
    }
}
