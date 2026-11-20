package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;

import java.util.UUID;

public record DriverResponse(
        UUID id,
        String fullName,
        String phone,
        String licenseNumber,
        String licenseImageUrl,
        VerificationStatus status,
        String rejectionReason,
        UUID reviewedBy
) {
}
