package iuh.fit.se.service;

import iuh.fit.se.domain.entity.EkycVerification;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.repository.EkycVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Reads the persisted verification status used by account and business-profile views. */
@Service
@RequiredArgsConstructor
public class EkycService {

    private final EkycVerificationRepository ekycVerificationRepository;

    /** Looks up an account's verification record without changing its stored state. */
    @Transactional(readOnly = true)
    public Optional<EkycVerification> findByAccountId(String accountId) {
        return ekycVerificationRepository.findByAccount_Id(UUID.fromString(accountId));
    }

    /** Returns true only when the account has completed eKYC with status VERIFIED. */
    @Transactional(readOnly = true)
    public boolean isVerified(UUID accountId) {
        return ekycVerificationRepository.findByAccount_Id(accountId)
                .map(verification -> verification.getStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
    }
}
