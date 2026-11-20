package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.dto.ContractResponse;
import iuh.fit.se.contractservice.dto.RequestSignOtpResponse;
import iuh.fit.se.contractservice.dto.SignContractRequest;
import iuh.fit.se.contractservice.mapper.ContractMapper;
import iuh.fit.se.contractservice.service.ContractService;
import iuh.fit.se.contractservice.service.IdentityVerificationClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;
    private final IdentityVerificationClient identityVerificationClient;
    private final ContractMapper contractMapper;

    @GetMapping("/mine")
    public List<ContractResponse> listMine(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) ContractStatus status
    ) {
        AccountRole accountRole = parseRole(role);
        return contractMapper.toResponses(contractService.list(accountId, accountRole, status));
    }

    @GetMapping("/{contractId}")
    public ContractResponse get(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID contractId
    ) {
        return contractMapper.toResponse(contractService.get(accountId, parseRole(role), contractId));
    }

    @PostMapping("/{contractId}/sign/otp")
    public RequestSignOtpResponse requestSignOtp(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @PathVariable UUID contractId
    ) {
        AccountRole accountRole = parseVerifiedPartyRole(role, accountId);
        return contractService.requestSignOtp(accountId, accountRole, contractId, ipAddress);
    }

    @PatchMapping("/{contractId}/sign")
    public ContractResponse sign(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @PathVariable UUID contractId,
            @Valid @RequestBody SignContractRequest request
    ) {
        AccountRole accountRole = parseVerifiedPartyRole(role, accountId);
        return contractMapper.toResponse(
                contractService.sign(accountId, accountRole, contractId, ipAddress, request.otpCode()));
    }

    private AccountRole parseVerifiedPartyRole(String role, UUID accountId) {
        AccountRole accountRole = parseRole(role);
        if (accountRole == AccountRole.SHIPPER || accountRole == AccountRole.CARRIER) {
            identityVerificationClient.assertVerified(accountId);
        }
        return accountRole;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Không thể xử lý yêu cầu ký hợp đồng.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(MethodArgumentNotValidException exception) {
        var firstError = exception.getBindingResult().getFieldErrors().stream().findFirst();
        String message = firstError.map(err -> Objects.requireNonNullElse(err.getDefaultMessage(), "Dữ liệu không hợp lệ"))
                .orElse("Dữ liệu không hợp lệ");
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", message));
    }

    private AccountRole parseRole(String role) {
        try {
            return AccountRole.valueOf(role.toUpperCase());
        } catch (Exception exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Unsupported account role");
        }
    }
}
