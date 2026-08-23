package iuh.fit.se.fleetservice.dto;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
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
    public static VehicleResponse from(Vehicle vehicle) {
        String regUrl = null;
        String inspUrl = null;
        if (vehicle.getDocuments() != null) {
            for (var doc : vehicle.getDocuments()) {
                if (doc.getDocumentType() == iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType.REGISTRATION) {
                    regUrl = doc.getDocumentUrl();
                } else if (doc.getDocumentType() == iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType.INSPECTION_CERTIFICATE) {
                    inspUrl = doc.getDocumentUrl();
                }
            }
        }

        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getCarrierId(),
                vehicle.getLicensePlate(),
                vehicle.getVehicleType(),
                vehicle.getBodyType(),
                vehicle.getPayloadCapacity(),
                vehicle.getStatus(),
                vehicle.getVerification() == null ? null : vehicle.getVerification().getStatus(),
                vehicle.getVerification() == null ? null : vehicle.getVerification().getNote(),
                regUrl,
                inspUrl
        );
    }
}
