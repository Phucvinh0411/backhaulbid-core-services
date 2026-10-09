package iuh.fit.se.mediaservice.controller;

import iuh.fit.se.mediaservice.service.MediaReadPolicy;
import iuh.fit.se.mediaservice.service.MediaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Service-to-service read of a private object: claim evidence, handover photos and carrier cargo-liability
 * certificates. Not routed by the gateway. The caller must hold the shared internal token and must already have
 * checked the owner of the trip, the claim or the admin review.
 */
@RestController
@RequestMapping("/internal/media")
public class InternalMediaController {
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern PRIVATE_IMAGE_KEY = Pattern.compile(
            "^handover-photos/" + UUID + "\\.(png|jpe?g|webp|gif)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_EVIDENCE_KEY = Pattern.compile(
            "^claim-evidence/" + UUID + "\\.(png|jpe?g|webp|gif|pdf)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_CERTIFICATE_KEY = Pattern.compile(
            "^business-verifications/insurance/" + UUID + "\\.(png|jpe?g|webp|gif|pdf)$", Pattern.CASE_INSENSITIVE);

    private final MediaService mediaService;
    private final String expectedToken;

    public InternalMediaController(MediaService mediaService, @Value("${app.internal-token:}") String expectedToken) {
        this.mediaService = mediaService;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/objects")
    public ResponseEntity<InputStreamResource> object(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                      @RequestParam("key") String key) {
        authorize(token);
        if (key == null || !(PRIVATE_IMAGE_KEY.matcher(key).matches() || PRIVATE_EVIDENCE_KEY.matcher(key).matches()
                || PRIVATE_CERTIFICATE_KEY.matcher(key).matches())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        }
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        String contentType = fileName.toLowerCase().endsWith(".pdf") ? "application/pdf" : MediaReadPolicy.contentType(fileName);
        var object = mediaService.open(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(object));
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || token == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
