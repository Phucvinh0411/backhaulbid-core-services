package iuh.fit.se.controller;

import iuh.fit.se.service.EkycReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.*;

@RestController @RequiredArgsConstructor
public class EkycReviewController {
    private final EkycReviewService reviews;
    public record Review(@Min(0) long version, @Pattern(regexp="APPROVE|REJECT") @NotNull String decision,
            @NotBlank @Size(max=500) String reason, boolean documentCompared, boolean faceCompared,
            @Size(max=255) String fullName) {}
    /** Resolves the authenticated account ID for report ownership and audit records. */
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }

    /** Returns the authenticated owner's short identity summary without caching it. */
    @GetMapping("/api/v1/representative-verifications/me/overview")
    @PreAuthorize("hasAnyRole('SHIPPER','CARRIER')")
    public ResponseEntity<EkycReviewService.OwnerSummary> mine(Authentication auth,
            @RequestParam(defaultValue="false") boolean reveal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.ownerOverview(actor(auth), reveal));
    }
    /** Streams evidence only when the requesting owner owns the report. */
    @GetMapping("/api/v1/representative-verifications/me/evidence/{id}/{part}")
    @PreAuthorize("hasAnyRole('SHIPPER','CARRIER')")
    public ResponseEntity<StreamingResponseBody> ownerImage(Authentication auth, @PathVariable UUID id, @PathVariable String part) {
        return image(reviews.ownerEvidence(actor(auth), id, part));
    }
    /** Lists paginated reports for the administrator review queue. */
    @GetMapping("/api/v1/admin/representative-verifications") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<EkycReviewService.QueueItem>> queue(Authentication auth,
            @RequestParam(defaultValue="PENDING") String status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.queue(actor(auth), status, page, pageSize));
    }
    /** Returns an administrator's detailed report view with sensitive fields revealed. */
    @GetMapping("/api/v1/admin/representative-verifications/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EkycReviewService.Overview> detail(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.adminOverview(actor(auth), id));
    }
    /** Streams evidence after the review service authorizes the administrator. */
    @GetMapping("/api/v1/admin/representative-verifications/{id}/evidence/{part}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StreamingResponseBody> adminImage(Authentication auth, @PathVariable UUID id, @PathVariable String part) {
        return image(reviews.adminEvidence(actor(auth), id, part));
    }
    /** Records an administrator decision after required document and face comparisons. */
    @PatchMapping("/api/v1/admin/representative-verifications/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EkycReviewService.Overview> review(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Review body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.review(actor(auth), id,
                body.version(), body.decision(), body.reason(), body.documentCompared(), body.faceCompared(), body.fullName()));
    }
    /** Builds a private, non-cacheable image response and erases decrypted bytes after streaming. */
    private ResponseEntity<StreamingResponseBody> image(byte[] bytes) {
        StreamingResponseBody stream=out -> { try { out.write(bytes); } finally { Arrays.fill(bytes, (byte)0); } };
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length)
                .cacheControl(CacheControl.noStore()).header("Pragma","no-cache").header("X-Content-Type-Options","nosniff")
                .body(stream);
    }
}
