package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.request.WithdrawalDecisionRequest;
import iuh.fit.se.walletservice.dto.response.WithdrawalPageResponse;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import iuh.fit.se.walletservice.dto.response.AdminWalletSummaryResponse;
import iuh.fit.se.walletservice.service.WalletQueryService;
import iuh.fit.se.walletservice.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Admin-only review endpoints; payout remains manual after approval. */
@RestController
@RequestMapping("/api/v1/admin/wallet-withdrawals")
public class AdminWithdrawalController {
    private final WithdrawalService withdrawalService;
    private final WalletQueryService walletQueryService;

    public AdminWithdrawalController(WithdrawalService withdrawalService, WalletQueryService walletQueryService) {
        this.withdrawalService = withdrawalService;
        this.walletQueryService = walletQueryService;
    }

    @GetMapping("/summary")
    public AdminWalletSummaryResponse summary(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(adminId, role);
        return walletQueryService.getAdminSummary();
    }

    @GetMapping
    public WithdrawalPageResponse list(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireAdmin(adminId, role);
        return withdrawalService.pending(status, page, pageSize);
    }

    @PatchMapping("/{withdrawalId}/approve")
    public WithdrawalResponse approve(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID withdrawalId) {
        UUID id = requireAdmin(adminId, role);
        return withdrawalService.approve(withdrawalId, id);
    }

    @PatchMapping("/{withdrawalId}/reject")
    public WithdrawalResponse reject(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID withdrawalId,
            @Valid @RequestBody(required = false) WithdrawalDecisionRequest decision) {
        UUID id = requireAdmin(adminId, role);
        return withdrawalService.reject(withdrawalId, id, decision);
    }

    private UUID requireAdmin(String adminId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required");
        }
        try {
            return UUID.fromString(adminId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator is required");
        }
    }
}
