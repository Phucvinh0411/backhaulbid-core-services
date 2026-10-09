package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.dto.AddClaimEvidenceRequest;
import iuh.fit.se.contractservice.dto.CreateInsuranceClaimRequest;
import iuh.fit.se.contractservice.dto.InsuranceClaimResponse;
import iuh.fit.se.contractservice.service.InsuranceClaimService;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Claim drafts for a trip, their private evidence, and the readiness check. Gateway routes
 * {@code /api/v1/trips/**} and {@code /api/v1/claims/**} here. Driver sessions have no route to any of these.
 */
@RestController
@RequiredArgsConstructor
public class InsuranceClaimController {
    private final InsuranceClaimService claims;

    @PostMapping("/api/v1/trips/{tripId}/claims")
    public ResponseEntity<InsuranceClaimResponse> create(@RequestHeader("X-User-Id") UUID accountId,
                                                         @RequestHeader("X-User-Role") String role,
                                                         @PathVariable UUID tripId,
                                                         @Valid @RequestBody CreateInsuranceClaimRequest request) {
        InsuranceClaimResponse created = claims.create(accountId, TripReviewController.parseRole(role), tripId, request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping("/api/v1/trips/{tripId}/claims")
    public ResponseEntity<List<InsuranceClaimResponse>> list(@RequestHeader("X-User-Id") UUID accountId,
                                                             @RequestHeader("X-User-Role") String role,
                                                             @PathVariable UUID tripId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.listForTrip(accountId, TripReviewController.parseRole(role), tripId));
    }

    @PostMapping("/api/v1/claims/{claimId}/evidence")
    public ResponseEntity<InsuranceClaimResponse> addEvidence(@RequestHeader("X-User-Id") UUID accountId,
                                                              @RequestHeader("X-User-Role") String role,
                                                              @PathVariable UUID claimId,
                                                              @Valid @RequestBody AddClaimEvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(claims.addEvidence(accountId, TripReviewController.parseRole(role), claimId, request));
    }

    /** Checks the checklist; the claim is marked ready for the provider, never sent. */
    @PostMapping("/api/v1/claims/{claimId}/ready")
    public ResponseEntity<InsuranceClaimResponse> markReady(@RequestHeader("X-User-Id") UUID accountId,
                                                            @RequestHeader("X-User-Role") String role,
                                                            @PathVariable UUID claimId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.markReady(accountId, TripReviewController.parseRole(role), claimId));
    }

    @GetMapping("/api/v1/claims/{claimId}/evidence/{evidenceId}/file")
    public ResponseEntity<byte[]> evidenceFile(@RequestHeader("X-User-Id") UUID accountId,
                                               @RequestHeader("X-User-Role") String role,
                                               @PathVariable UUID claimId,
                                               @PathVariable UUID evidenceId) {
        InsuranceClaimService.EvidenceFile file = claims.evidenceFile(accountId, TripReviewController.parseRole(role), claimId, evidenceId);
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
