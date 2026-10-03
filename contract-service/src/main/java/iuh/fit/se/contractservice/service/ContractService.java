package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.ContractSignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {
    private final ContractRepository contractRepository;
    private final ContractSignatureRepository signatureRepository;

    @Transactional(readOnly = true)
    public List<Contract> list(UUID accountId, AccountRole role, ContractStatus status) {
        List<Contract> contracts = switch (role) {
            case ADMIN -> contractRepository.findAllByOrderByCreatedAtDesc();
            case CARRIER -> contractRepository.findByCarrierIdOrderByCreatedAtDesc(accountId);
            case DRIVER -> List.of();
            case SHIPPER -> contractRepository.findByShipperIdOrderByCreatedAtDesc(accountId);
        };
        return status == null ? contracts : contracts.stream().filter(contract -> contract.getStatus() == status).toList();
    }

    @Transactional(readOnly = true)
    public Contract get(UUID accountId, AccountRole role, UUID contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        ensureAccess(contract, accountId, role);
        return contract;
    }

    @Transactional
    public Contract sign(UUID accountId, AccountRole role, UUID contractId, String ipAddress) {
        if (role != AccountRole.CARRIER && role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only contract parties can sign");
        }
        Contract contract = get(accountId, role, contractId);
        if (contract.getStatus() == ContractStatus.CANCELLED || contract.getStatus() == ContractStatus.SIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract cannot be signed in its current status");
        }

        boolean newSignature = signatureRepository.findByContractIdAndAccountId(contractId, accountId).isEmpty();
        ContractSignature signature = signatureRepository.findByContractIdAndAccountId(contractId, accountId)
                .orElseGet(() -> ContractSignature.builder()
                        .contract(contract)
                        .accountId(accountId)
                        .role(role)
                        .ipAddress(ipAddress)
                        .build());
        if (signature.getSignedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract signature already recorded");
        }
        signature.setIpAddress(ipAddress);
        signature.setRole(role);
        signature.sign();
        if (newSignature) {
            contract.addSignature(signature);
        }
        signatureRepository.save(signature);

        contract.setStatus(contract.isFullySigned() ? ContractStatus.SIGNED : ContractStatus.WAITING_SIGNATURE);
        return contractRepository.save(contract);
    }

    private void ensureAccess(Contract contract, UUID accountId, AccountRole role) {
        boolean allowed = role == AccountRole.ADMIN
                || (role == AccountRole.CARRIER && accountId.equals(contract.getCarrierId()))
                || (role == AccountRole.SHIPPER && accountId.equals(contract.getShipperId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this contract");
        }
    }
}
