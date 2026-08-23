package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.dto.ContractResponse;
import iuh.fit.se.contractservice.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;

    @GetMapping("/mine")
    public List<ContractResponse> listMine(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) ContractStatus status
    ) {
        AccountRole accountRole = parseRole(role);
        return contractService.list(accountId, accountRole, status).stream().map(ContractResponse::from).toList();
    }

    @GetMapping("/{contractId}")
    public ContractResponse get(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID contractId
    ) {
        return ContractResponse.from(contractService.get(accountId, parseRole(role), contractId));
    }

    @PatchMapping("/{contractId}/sign")
    public ContractResponse sign(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @PathVariable UUID contractId
    ) {
        return ContractResponse.from(contractService.sign(accountId, parseRole(role), contractId, ipAddress));
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
