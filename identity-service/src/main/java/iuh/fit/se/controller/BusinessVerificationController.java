package iuh.fit.se.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import iuh.fit.se.config.OpenApiConfig;
import iuh.fit.se.dto.BusinessLookupResponse;
import iuh.fit.se.dto.BusinessVerificationResponse;
import iuh.fit.se.dto.BusinessVerificationReviewRequest;
import iuh.fit.se.dto.BusinessVerificationPageResponse;
import iuh.fit.se.mapper.BusinessVerificationMapper;
import iuh.fit.se.service.BusinessVerificationService;
import iuh.fit.se.service.CompanyVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/business-verifications")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BusinessVerificationController {

    private final BusinessVerificationService businessVerificationService;
    private final CompanyVerificationService companyVerificationService;
    private final BusinessVerificationMapper businessVerificationMapper;

    @GetMapping("/lookup/{taxCode}")
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public ResponseEntity<BusinessLookupResponse> lookup(
            @PathVariable
            @Pattern(regexp = "\\d{10}(\\d{3})?", message = "Tax code must contain 10 or 13 digits")
            String taxCode) {
        return ResponseEntity.ok(businessVerificationService.lookupByTaxCode(taxCode));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public ResponseEntity<BusinessVerificationResponse> submit(
            Authentication authentication,
            @RequestParam
            @Pattern(regexp = "\\d{10}(\\d{3})?",
                    message = "Tax code must contain 10 or 13 digits")
            String taxCode,
            @RequestParam(required = false) String ekycRepresentativeName,
            @RequestParam String businessLicenseUrl,
            @RequestParam(required = false) String authorizationLetterUrl) {
        return ResponseEntity.ok(companyVerificationService.submit(
                authentication.getName(),
                taxCode,
                ekycRepresentativeName,
                businessLicenseUrl,
                authorizationLetterUrl));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
    public ResponseEntity<BusinessVerificationResponse> current(
            Authentication authentication) {
        return ResponseEntity.ok(
                companyVerificationService.findCurrent(authentication.getName())
                        .orElseGet(businessVerificationMapper::notSubmitted)
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BusinessVerificationPageResponse> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(companyVerificationService.findByStatus(
                status, page, pageSize));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BusinessVerificationResponse> review(
            @PathVariable UUID id,
            @Valid @RequestBody BusinessVerificationReviewRequest request) {
        return ResponseEntity.ok(companyVerificationService.review(
                id, request.decision(), request.rejectionReason()));
    }

}
