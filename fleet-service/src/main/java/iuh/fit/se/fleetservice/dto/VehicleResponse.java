package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID carrierId,
        String licensePlate,
        VehicleType vehicleType,
        String bodyType,
        BigDecimal payloadCapacity,
        VehicleStatus status,
        VerificationStatus verificationStatus,
        String rejectionReason,
        String registrationDocumentUrl,
        String inspectionDocumentUrl
) {
}
