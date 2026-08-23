package iuh.fit.se.controller;

import iuh.fit.se.domain.dto.request.AdminSettingsRequest;
import iuh.fit.se.domain.dto.response.AdminSettingsResponse;
import iuh.fit.se.service.AdminSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {
    private final AdminSettingsService adminSettingsService;

    @GetMapping("/{scope}")
    public AdminSettingsResponse get(@PathVariable String scope) {
        return adminSettingsService.get(scope);
    }

    @PutMapping("/{scope}")
    public AdminSettingsResponse save(
            Authentication authentication,
            @PathVariable String scope,
            @Valid @RequestBody AdminSettingsRequest request
    ) {
        return adminSettingsService.save(UUID.fromString(authentication.getName()), scope, request);
    }
}
