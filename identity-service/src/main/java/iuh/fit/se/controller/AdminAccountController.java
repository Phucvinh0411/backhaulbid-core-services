package iuh.fit.se.controller;

import iuh.fit.se.domain.dto.request.AccountStatusUpdateRequest;
import iuh.fit.se.domain.dto.response.AdminAccountResponse;
import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {
    private final AdminAccountService adminAccountService;

    @GetMapping
    public List<AdminAccountResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AccountRole role,
            @RequestParam(required = false) AccountStatus status
    ) {
        return adminAccountService.list(search, role, status);
    }

    @PatchMapping("/{accountId}/status")
    public AdminAccountResponse updateStatus(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountStatusUpdateRequest request
    ) {
        return adminAccountService.updateStatus(UUID.fromString(authentication.getName()), accountId, request);
    }
}
