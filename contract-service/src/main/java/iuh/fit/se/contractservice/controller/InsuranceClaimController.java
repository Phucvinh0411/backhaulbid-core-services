package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.dto.AddClaimEvidenceRequest;
import iuh.fit.se.contractservice.dto.CarrierClaimResponse;
import iuh.fit.se.contractservice.dto.CreateInsuranceClaimRequest;
import iuh.fit.se.contractservice.dto.InsuranceClaimResponse;
import iuh.fit.se.contractservice.service.ClaimPackageService;
import iuh.fit.se.contractservice.service.InsuranceClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
 * Claim drafts for a trip, their private evidence, the readiness check, sharing with the carrier, and the claim pack.
 * Gateway routes {@code /api/v1/trips/**} and {@code /api/v1/claims/**} here. Driver sessions have no route to any of these.
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

    /** The trip's claims for its shipper or an admin. A carrier reads shared claims through {@code /api/v1/claims/shared}. */
    @GetMapping("/api/v1/trips/{tripId}/claims")
    public ResponseEntity<List<InsuranceClaimResponse>> list(@RequestHeader("X-User-Id") UUID accountId,
                                                             @RequestHeader("X-User-Role") String role,
                                                             @PathVariable UUID tripId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.listForTrip(accountId, TripReviewController.parseRole(role), tripId));
    }

    /** The claims shared with the caller's carrier account, across its trips. Nothing before a share. */
    @GetMapping("/api/v1/claims/shared")
    public ResponseEntity<List<CarrierClaimResponse>> shared(@RequestHeader("X-User-Id") UUID accountId,
                                                             @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.sharedWithCarrier(accountId, TripReviewController.parseRole(role)));
    }

    @PostMapping("/api/v1/claims/{claimId}/evidence")
    public ResponseEntity<InsuranceClaimResponse> addEvidence(@RequestHeader("X-User-Id") UUID accountId,
                                                              @RequestHeader("X-User-Role") String role,
                                                              @PathVariable UUID claimId,
                                                              @Valid @RequestBody AddClaimEvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(claims.addEvidence(accountId, TripReviewController.parseRole(role), claimId, request));
    }

    /** Checks the checklist. A complete claim becomes READY_TO_SHARE: ready to share, never sent. */
    @PostMapping("/api/v1/claims/{claimId}/ready")
    public ResponseEntity<InsuranceClaimResponse> markReady(@RequestHeader("X-User-Id") UUID accountId,
                                                            @RequestHeader("X-User-Role") String role,
                                                            @PathVariable UUID claimId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.markReady(accountId, TripReviewController.parseRole(role), claimId));
    }

    /** The shipper shares the ready claim with the trip's carrier. */
    @PostMapping("/api/v1/claims/{claimId}/share")
    public ResponseEntity<InsuranceClaimResponse> share(@RequestHeader("X-User-Id") UUID accountId,
                                                        @RequestHeader("X-User-Role") String role,
                                                        @PathVariable UUID claimId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.share(accountId, TripReviewController.parseRole(role), claimId));
    }

    /** The carrier confirms it received the shared claim. Receipt only; no insurer decision is implied. */
    @PostMapping("/api/v1/claims/{claimId}/acknowledge")
    public ResponseEntity<CarrierClaimResponse> acknowledge(@RequestHeader("X-User-Id") UUID accountId,
                                                            @RequestHeader("X-User-Role") String role,
                                                            @PathVariable UUID claimId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(claims.acknowledge(accountId, TripReviewController.parseRole(role), claimId));
    }

    /** The claim pack as a ZIP: a summary and the files, each read through the private proxy first. */
    @GetMapping("/api/v1/claims/{claimId}/package")
    public ResponseEntity<byte[]> pack(@RequestHeader("X-User-Id") UUID accountId,
                                       @RequestHeader("X-User-Role") String role,
                                       @PathVariable UUID claimId) {
        ClaimPackageService.Packaged packaged = claims.packageFor(accountId, TripReviewController.parseRole(role), claimId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(packaged.fileName()).build().toString())
                .body(packaged.zip());
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
