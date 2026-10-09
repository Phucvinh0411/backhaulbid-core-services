package iuh.fit.se.dto;

import java.time.LocalDate;
import java.util.UUID;

/** cargoLiabilityVerifiedUntil is set only while an admin-verified, unexpired cargo-liability certificate exists. */
public record CarrierPublicProfileResponse(
        UUID accountId,
        String contactEmail,
        String contactPhone,
        String companyName,
        String address,
        String legalRepresentative,
        String taxCode,
        String verificationStatus,
        LocalDate cargoLiabilityVerifiedUntil
) {
}
