package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.response.WalletResponse;
import iuh.fit.se.walletservice.service.WalletQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/wallets")
public class AdminWalletController {
    private final WalletQueryService walletQueryService;

    public AdminWalletController(WalletQueryService walletQueryService) {
        this.walletQueryService = walletQueryService;
    }

    @GetMapping("/{accountId}")
    public WalletResponse getWallet(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID accountId) {
        requireAdmin(adminId, role);
        return walletQueryService.getAdminWallet(accountId);
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
