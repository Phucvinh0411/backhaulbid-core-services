package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.dto.AddCargoDocumentRequest;
import iuh.fit.se.contractservice.dto.CargoDocumentResponse;
import iuh.fit.se.contractservice.dto.CargoDocumentsResponse;
import iuh.fit.se.contractservice.service.TripCargoDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The goods-value documents of one trip. Only the trip's shipper and admins reach these routes. Carriers and drivers
 * get no document data from here; a carrier receives them only inside a shared claim pack.
 */
@RestController
@RequiredArgsConstructor
public class TripCargoDocumentController {
    private final TripCargoDocumentService cargoDocuments;

    @GetMapping("/api/v1/trips/{tripId}/cargo-documents")
    public ResponseEntity<CargoDocumentsResponse> list(@RequestHeader("X-User-Id") UUID accountId,
                                                       @RequestHeader("X-User-Role") String role,
                                                       @PathVariable UUID tripId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(cargoDocuments.view(accountId, TripReviewController.parseRole(role), tripId));
    }

    /** The shipper adds a document uploaded to the private folder after the award. */
    @PostMapping("/api/v1/trips/{tripId}/cargo-documents")
    public ResponseEntity<CargoDocumentResponse> add(@RequestHeader("X-User-Id") UUID accountId,
                                                     @RequestHeader("X-User-Role") String role,
                                                     @PathVariable UUID tripId,
                                                     @Valid @RequestBody AddCargoDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(cargoDocuments.addSupplemental(accountId, TripReviewController.parseRole(role), tripId, request));
    }

    @GetMapping("/api/v1/trips/{tripId}/cargo-documents/{documentId}/file")
    public ResponseEntity<byte[]> file(@RequestHeader("X-User-Id") UUID accountId,
                                       @RequestHeader("X-User-Role") String role,
                                       @PathVariable UUID tripId,
                                       @PathVariable UUID documentId) {
        TripCargoDocumentService.CargoFile file = cargoDocuments.fileOf(accountId, TripReviewController.parseRole(role), tripId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(file.bytes());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> businessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Request could not be completed")));
    }
}
