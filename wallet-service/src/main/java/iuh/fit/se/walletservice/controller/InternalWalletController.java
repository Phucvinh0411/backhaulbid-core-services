package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.domain.operation.InternalWalletOperation;
import iuh.fit.se.walletservice.dto.request.InternalWalletOperationRequest;
import iuh.fit.se.walletservice.dto.request.InternalWalletReleaseRequest;
import iuh.fit.se.walletservice.dto.response.InternalWalletOperationResponse;
import iuh.fit.se.walletservice.mapper.InternalWalletOperationMapper;
import iuh.fit.se.walletservice.service.InternalWalletService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Internal wallet contract consumed by backend services such as bidding-service.
 *
 * The controller owns transport concerns only: authentication at the service
 * boundary, request validation and mapping the domain result to an API DTO.
 * Wallet mutations remain inside {@link InternalWalletService}.
 */
@RestController
@RequestMapping("/internal")
public class InternalWalletController {
    private final InternalWalletService internalWalletService;
    private final InternalWalletOperationMapper internalWalletOperationMapper;
    private final String expectedToken;

    public InternalWalletController(
            InternalWalletService internalWalletService,
            InternalWalletOperationMapper internalWalletOperationMapper,
            @Value("${INTERNAL_WALLET_TOKEN}") String expectedToken) {
        this.internalWalletService = internalWalletService;
        this.internalWalletOperationMapper = internalWalletOperationMapper;
        this.expectedToken = expectedToken;
    }

    /** Creates a frozen wallet hold before an auction registration proceeds. */
    @PostMapping("/wallets/{accountId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public InternalWalletOperationResponse hold(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody InternalWalletOperationRequest request) {
        authorize(token);
        InternalWalletOperation operation = internalWalletService.hold(accountId, request);
        return internalWalletOperationMapper.toResponse(operation);
    }

    /** Charges a wallet participation fee using the request idempotency key. */
    @PostMapping("/wallets/{accountId}/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public InternalWalletOperationResponse charge(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody InternalWalletOperationRequest request) {
        authorize(token);
        InternalWalletOperation operation = internalWalletService.charge(accountId, request);
        return internalWalletOperationMapper.toResponse(operation);
    }

    /** Releases a previously created deposit hold. */
    @PostMapping("/wallet-holds/{holdId}/release")
    public InternalWalletOperationResponse release(
            @PathVariable UUID holdId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody InternalWalletReleaseRequest request) {
        authorize(token);
        InternalWalletOperation operation = internalWalletService.release(holdId, request);
        return internalWalletOperationMapper.toResponse(operation);
    }

    /** Converts a deposit hold into a penalty transaction when registration fails. */
    @PostMapping("/wallet-holds/{holdId}/forfeit")
    public InternalWalletOperationResponse forfeit(
            @PathVariable UUID holdId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody InternalWalletReleaseRequest request) {
        authorize(token);
        InternalWalletOperation operation = internalWalletService.forfeit(holdId, request);
        return internalWalletOperationMapper.toResponse(operation);
    }

    private void authorize(String token) {
        if (!expectedToken.equals(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid internal wallet token");
        }
    }
}
