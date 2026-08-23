package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.response.WalletResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionPageResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionResponse;
import iuh.fit.se.walletservice.service.WalletQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletQueryService walletQueryService;

    public WalletController(WalletQueryService walletQueryService) {
        this.walletQueryService = walletQueryService;
    }

    @GetMapping("/me")
    public WalletResponse getMyWallet(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return walletQueryService.getWallet(accountId(userId));
    }

    @GetMapping("/me/transactions")
    public WalletTransactionPageResponse getMyTransactions(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return walletQueryService.getTransactions(accountId(userId), page, pageSize);
    }

    @GetMapping("/me/transactions/{transactionId}")
    public WalletTransactionResponse getMyTransaction(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID transactionId) {
        return walletQueryService.getTransaction(accountId(userId), transactionId);
    }

    private UUID accountId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is required");
        }
    }
}
