package iuh.fit.se.domain.dto.response;

import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import iuh.fit.se.domain.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminAccountResponse(
        UUID id,
        String displayName,
        String email,
        String phone,
        AccountRole role,
        AccountStatus status,
        VerificationStatus verificationStatus,
        String companyName,
        Instant registeredAt
) {
}
