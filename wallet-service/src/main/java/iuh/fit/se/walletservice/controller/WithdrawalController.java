package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.request.WithdrawalRequestDto;
import iuh.fit.se.walletservice.dto.response.WithdrawalPageResponse;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import iuh.fit.se.walletservice.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** User-facing withdrawal request endpoints. */
@RestController
@RequestMapping("/api/v1/wallets/me/withdrawals")
public class WithdrawalController {
    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WithdrawalResponse create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody WithdrawalRequestDto request) {
        return withdrawalService.create(accountId(userId), request);
    }

    @GetMapping
    public WithdrawalPageResponse mine(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return withdrawalService.mine(accountId(userId), page, pageSize);
    }

    private UUID accountId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is required");
        }
    }
}
