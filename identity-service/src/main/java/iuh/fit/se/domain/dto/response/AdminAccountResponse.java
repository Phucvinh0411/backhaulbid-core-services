package iuh.fit.se.domain.dto.response;

import iuh.fit.se.domain.entity.Account;
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
    public static AdminAccountResponse from(Account account) {
        String profileName = account.getUserProfile() == null ? null : account.getUserProfile().getFullName();
        String companyName = account.getCompany() == null ? null : account.getCompany().getCompanyName();
        String displayName = firstNonBlank(profileName, companyName, account.getEmail(), account.getPhone());
        VerificationStatus verificationStatus = account.getCompany() != null
                ? account.getCompany().getVerificationStatus()
                : account.getEkycVerification() == null ? null : account.getEkycVerification().getStatus();
        return new AdminAccountResponse(
                account.getId(),
                displayName,
                account.getEmail(),
                account.getPhone(),
                account.getRole(),
                account.getStatus(),
                verificationStatus,
                companyName,
                account.getCreatedAt()
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "Chưa có thông tin";
    }
}
