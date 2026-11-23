package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.dto.RequestSignOtpResponse;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.ContractSignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {
    private final ContractRepository contractRepository;
    private final ContractSignatureRepository signatureRepository;
    private final IdentityVerificationClient identityVerificationClient;
    private final ContractSigningOtpService signingOtpService;
    private final ContractOtpMailSender otpMailSender;
    private final AwardEventPublisher awardEventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Contract> list(UUID accountId, AccountRole role, ContractStatus status) {
        List<Contract> contracts = switch (role) {
            case ADMIN -> contractRepository.findAllByOrderByCreatedAtDesc();
            case CARRIER -> contractRepository.findByCarrierIdOrderByCreatedAtDesc(accountId);
            case DRIVER -> List.of();
            case SHIPPER -> contractRepository.findByShipperIdOrderByCreatedAtDesc(accountId);
        };
        List<Contract> filtered = status == null
                ? contracts
                : contracts.stream().filter(contract -> contract.getStatus() == status).toList();
        filtered.forEach(this::initializeLazyAssociations);
        return filtered;
    }

    @Transactional(readOnly = true)
    public Contract get(UUID accountId, AccountRole role, UUID contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        ensureAccess(contract, accountId, role);
        initializeLazyAssociations(contract);
        return contract;
    }

    private void initializeLazyAssociations(Contract contract) {
        if (contract.getTrip() != null && contract.getTrip().getTrackingLogs() != null) {
            contract.getTrip().getTrackingLogs().size();
        }
    }

    @Transactional
    public RequestSignOtpResponse requestSignOtp(UUID accountId, AccountRole role, UUID contractId, String ipAddress) {
        Instant now = clock.instant();
        String sanitizedIp = sanitizeIp(ipAddress);
        Contract contract = loadSignableContract(accountId, role, contractId, now);

        var existingSignature = signatureRepository.findByContractIdAndAccountId(contractId, accountId);
        boolean newSignature = existingSignature.isEmpty();
        ContractSignature signature = existingSignature.orElseGet(() -> ContractSignature.builder()
                .contract(contract)
                .accountId(accountId)
                .role(role)
                .ipAddress(sanitizedIp)
                .build());
        signingOtpService.assertCanRequestOtp(signature, now);

        var contact = identityVerificationClient.fetchSigningContact(accountId);
        if (contact.email() == null || contact.email().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tài khoản chưa có email. Vui lòng thêm email trong Cài đặt tài khoản trước khi ký hợp đồng.");
        }

        var issued = signingOtpService.issueChallenge(signature, contractId, accountId, now);
        signature.setIpAddress(sanitizedIp);
        signature.setRole(role);
        if (newSignature) {
            contract.addSignature(signature);
        }
        signatureRepository.save(signature);

        otpMailSender.sendSigningOtp(
                contact.email(),
                contact.fullName(),
                contract.getContractCode(),
                issued.otp(),
                issued.expiresMinutes());
        return new RequestSignOtpResponse(
                signingOtpService.maskEmail(contact.email()),
                issued.expiresAt(),
                issued.resendAfterSeconds());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Contract sign(UUID accountId, AccountRole role, UUID contractId, String ipAddress, String otpCode) {
        Instant now = clock.instant();
        Contract contract = loadSignableContract(accountId, role, contractId, now);
        boolean auctionAwardContract = contract.getTrip() != null
                && contract.getTrip().getAwardAttemptId() != null;

        ContractSignature signature = signatureRepository.findByContractIdAndAccountId(contractId, accountId)
                .orElse(null);
        if (!signingOtpService.verifyChallenge(signature, contractId, accountId, otpCode, now)) {
            signatureRepository.save(signature);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã OTP không chính xác. Vui lòng kiểm tra email và thử lại.");
        }

        signature.setIpAddress(sanitizeIp(ipAddress));
        signature.setRole(role);
        signature.sign();
        signatureRepository.save(signature);

        contract.setStatus(contract.isFullySigned() ? ContractStatus.SIGNED : ContractStatus.WAITING_SIGNATURE);
        if (auctionAwardContract && role == AccountRole.CARRIER && contract.getStatus() != ContractStatus.SIGNED) {
            contract.setSigningDeadlineAt(now.plus(Duration.ofHours(24)));
        }
        Contract saved = contractRepository.save(contract);
        if (saved.getSignatures() != null) {
            saved.getSignatures().size();
        }
        if (auctionAwardContract) {
            awardEventPublisher.publishAfterCommit(
                    contract.getTrip().getAwardAttemptId(),
                    saved.getId(),
                    contract.getTrip().getId(),
                    saved.getStatus());
        }
        return saved;
    }

    private Contract loadSignableContract(UUID accountId, AccountRole role, UUID contractId, Instant now) {
        if (role != AccountRole.CARRIER && role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only contract parties can sign");
        }
        Contract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        ensureAccess(contract, accountId, role);
        if (contract.getStatus() == ContractStatus.CANCELLED
                || contract.getStatus() == ContractStatus.EXPIRED
                || contract.getStatus() == ContractStatus.SIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract cannot be signed in its current status");
        }
        if (contract.getSigningDeadlineAt() != null && now.isAfter(contract.getSigningDeadlineAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract signing deadline has passed");
        }
        boolean auctionAwardContract = contract.getTrip() != null
                && contract.getTrip().getAwardAttemptId() != null;
        if (auctionAwardContract && role == AccountRole.SHIPPER && !hasSigned(contract, AccountRole.CARRIER)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Carrier must sign the contract first");
        }
        return contract;
    }

    private boolean hasSigned(Contract contract, AccountRole role) {
        return contract.getSignatures() != null && contract.getSignatures().stream()
                .anyMatch(signature -> signature.getRole() == role && signature.getSignedAt() != null);
    }

    static String sanitizeIp(String ipAddress) {
        if (ipAddress == null) return null;
        String first = ipAddress.split(",")[0].trim();
        if (first.isEmpty()) return null;
        return first.length() > 45 ? first.substring(0, 45) : first;
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
