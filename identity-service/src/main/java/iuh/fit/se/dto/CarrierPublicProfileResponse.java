package iuh.fit.se.dto;

import java.util.UUID;

public record CarrierPublicProfileResponse(
        UUID accountId,
        String contactEmail,
        String contactPhone,
        String companyName,
        String address,
        String legalRepresentative,
        String taxCode,
        String verificationStatus
) {
}
