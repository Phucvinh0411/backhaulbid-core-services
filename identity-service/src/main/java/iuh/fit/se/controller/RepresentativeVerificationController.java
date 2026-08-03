package iuh.fit.se.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import iuh.fit.se.config.OpenApiConfig;
import iuh.fit.se.dto.EkycSubmitRequest;
import iuh.fit.se.dto.RepresentativeVerificationResponse;
import iuh.fit.se.mapper.RepresentativeVerificationMapper;
import iuh.fit.se.service.EkycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/representative-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class RepresentativeVerificationController {

    private final EkycService ekycService;
    private final RepresentativeVerificationMapper representativeVerificationMapper;

    @PostMapping
    public ResponseEntity<RepresentativeVerificationResponse> submit(
            Authentication authentication,
            @Valid @RequestBody EkycSubmitRequest request) {
        return ResponseEntity.ok(representativeVerificationMapper.toResponse(
                ekycService.processEkyc(authentication.getName(), request)));
    }

    @GetMapping("/me")
    public ResponseEntity<RepresentativeVerificationResponse> current(
            Authentication authentication) {
        return ResponseEntity.ok(
                ekycService.findByAccountId(authentication.getName())
                        .map(representativeVerificationMapper::toResponse)
                        .orElseGet(representativeVerificationMapper::notSubmitted)
        );
    }
}
