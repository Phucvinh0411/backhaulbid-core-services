package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;

import java.util.UUID;

public record PublicDriverResponse(
        UUID id,
        String fullName,
        String phone,
        String licenseNumber,
        VerificationStatus status
) {
}
