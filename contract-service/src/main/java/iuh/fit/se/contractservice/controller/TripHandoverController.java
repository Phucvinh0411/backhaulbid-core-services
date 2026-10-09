package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.dto.ConfirmTripHandoverRequest;
import iuh.fit.se.contractservice.dto.RecordTripHandoverRequest;
import iuh.fit.se.contractservice.dto.TripHandoverResponse;
import iuh.fit.se.contractservice.service.MediaEvidenceClient;
import iuh.fit.se.contractservice.service.TripHandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pickup handover of one trip. Gateway routes {@code /api/v1/trips/**} here; driver sessions reach GET, PUT and photo GET only. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}/handover")
@RequiredArgsConstructor
public class TripHandoverController {
    private final TripHandoverService handovers;
    private final MediaEvidenceClient media;

    /** Records or replaces the handover before pickup (driver session or carrier). */
    @PutMapping
    public TripHandoverResponse record(@RequestHeader("X-User-Id") UUID accountId,
                                       @RequestHeader("X-User-Role") String role,
                                       @PathVariable UUID tripId,
                                       @Valid @RequestBody RecordTripHandoverRequest request) {
        return TripHandoverResponse.from(handovers.record(accountId, TripReviewController.parseRole(role), tripId, request));
    }

    /** The shipper's confirmation that the goods were handed over. */
    @PostMapping("/confirm")
    public TripHandoverResponse confirm(@RequestHeader("X-User-Id") UUID accountId,
                                        @RequestHeader("X-User-Role") String role,
                                        @PathVariable UUID tripId,
                                        @Valid @RequestBody ConfirmTripHandoverRequest request) {
        return TripHandoverResponse.from(handovers.confirm(accountId, TripReviewController.parseRole(role), tripId, request));
    }

    /** The trip's handover as an array of zero or one item. */
    @GetMapping
    public List<TripHandoverResponse> view(@RequestHeader("X-User-Id") UUID accountId,
                                           @RequestHeader("X-User-Role") String role,
                                           @PathVariable UUID tripId) {
        return handovers.view(accountId, TripReviewController.parseRole(role), tripId).stream()
                .map(TripHandoverResponse::from).toList();
    }

    /**
     * One handover photo, streamed after the same trip check as the record. The bytes come from the private media
     * folder through the internal channel; the storage key is never returned.
     */
    @GetMapping("/photos/{index}")
    public ResponseEntity<byte[]> photo(@RequestHeader("X-User-Id") UUID accountId,
                                        @RequestHeader("X-User-Role") String role,
                                        @PathVariable UUID tripId,
                                        @PathVariable int index) {
        String key = handovers.photoKeyAt(accountId, TripReviewController.parseRole(role), tripId, index);
        MediaEvidenceClient.Fetched fetched = media.fetch(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Chưa tải được ảnh bàn giao. Vui lòng thử lại sau."));
        return ResponseEntity.ok()
                .contentType(safeMediaType(fetched.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(fetched.bytes());
    }

    private static MediaType safeMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (InvalidMediaTypeException unknown) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> businessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Request could not be completed")));
    }
}
