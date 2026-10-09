package iuh.fit.se.mediaservice.controller;

import iuh.fit.se.mediaservice.service.DriverMediaAccess;
import iuh.fit.se.mediaservice.service.MediaReadPolicy;
import iuh.fit.se.mediaservice.service.MediaService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;
    private final DriverMediaAccess driverAccess;

    public MediaController(MediaService mediaService, DriverMediaAccess driverAccess) {
        this.mediaService = mediaService;
        this.driverAccess = driverAccess;
    }

    /**
     * Streams an operational image for signed-in users. The gateway authenticates every
     * /api/v1/media request and injects X-User-Id; object keys are random UUIDs returned
     * only through APIs that already check access to the trip, auction or complaint. Avatar
     * objects are account profile images and are available to signed-in users.
     */
    @GetMapping("/files/{folder}/{fileName}")
    public ResponseEntity<InputStreamResource> readFile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Type", required = false) String authType,
            @PathVariable String folder,
            @PathVariable String fileName) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String key = MediaReadPolicy.readableKey(folder, fileName);
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        // A driver session only reads images attached to its own trip; 404 hides whether other files exist.
        if (DriverMediaAccess.DRIVER_ASSIGNMENT.equals(authType) && !driverAccess.canRead(userId, key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        var object = mediaService.open(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaReadPolicy.contentType(fileName)))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(object));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Type", required = false) String authType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "uploads") String folder) {
        // A driver session may only add delivery proof images while it can still write to its trip.
        if (DriverMediaAccess.DRIVER_ASSIGNMENT.equals(authType) && !driverAccess.canUpload(userId, folder)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Phiên tài xế không được tải tệp này lên."));
        }

        try {
            MediaService.UploadedObject uploaded = mediaService.uploadFile(file, folder);
            Map<String, String> response = new HashMap<>();
            // The key is what private readers use; the url is kept so older clients keep working.
            response.put("key", uploaded.key());
            response.put("url", uploaded.url());
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException refused) {
            throw refused;
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Lỗi upload file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
