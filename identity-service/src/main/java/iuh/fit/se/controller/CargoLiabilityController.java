package iuh.fit.se.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import iuh.fit.se.config.OpenApiConfig;
import iuh.fit.se.dto.CargoLiabilityDtos;
import iuh.fit.se.service.CargoLiabilityService;
import iuh.fit.se.service.MediaCertificateClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cargo-liability")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CargoLiabilityController {

    private final CargoLiabilityService cargoLiabilityService;

    @PostMapping
    @PreAuthorize("hasRole('CARRIER')")
    public ResponseEntity<CargoLiabilityDtos.Submission> submit(
            Authentication authentication,
            @Valid @RequestBody CargoLiabilityDtos.SubmitRequest request) {
        return ResponseEntity.ok(cargoLiabilityService.submit(authentication.getName(), request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CARRIER')")
    public ResponseEntity<CargoLiabilityDtos.Status> current(Authentication authentication) {
        return ResponseEntity.ok(cargoLiabilityService.current(authentication.getName()));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CargoLiabilityDtos.ReviewPage> reviews(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(cargoLiabilityService.findForReview(status, page, pageSize));
    }

    @GetMapping("/reviews/{id}/certificate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> certificate(@PathVariable UUID id) {
        MediaCertificateClient.Certificate certificate = cargoLiabilityService.certificate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(certificate.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(certificate.bytes());
    }

    @PatchMapping("/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CargoLiabilityDtos.Submission> review(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody CargoLiabilityDtos.ReviewRequest request) {
        return ResponseEntity.ok(cargoLiabilityService.review(
                id, authentication.getName(), request.decision(), request.rejectionReason()));
    }
}
