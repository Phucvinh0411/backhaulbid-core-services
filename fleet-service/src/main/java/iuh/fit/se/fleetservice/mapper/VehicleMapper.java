package iuh.fit.se.fleetservice.mapper;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType;
import iuh.fit.se.fleetservice.dto.PublicVehicleResponse;
import iuh.fit.se.fleetservice.dto.VehicleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VehicleMapper {

    @Mapping(target = "verificationStatus", source = "verification.status")
    @Mapping(target = "rejectionReason", source = "verification.note")
    @Mapping(target = "registrationDocumentUrl", expression = "java(findRegistrationDocumentUrl(vehicle))")
    @Mapping(target = "inspectionDocumentUrl", expression = "java(findInspectionDocumentUrl(vehicle))")
    VehicleResponse toResponse(Vehicle vehicle);

    List<VehicleResponse> toResponses(List<Vehicle> vehicles);

    PublicVehicleResponse toPublicResponse(Vehicle vehicle);

    List<PublicVehicleResponse> toPublicResponses(List<Vehicle> vehicles);

    default String findRegistrationDocumentUrl(Vehicle vehicle) {
        return findDocumentUrl(vehicle, VehicleDocumentType.REGISTRATION);
    }

    default String findInspectionDocumentUrl(Vehicle vehicle) {
        return findDocumentUrl(vehicle, VehicleDocumentType.INSPECTION_CERTIFICATE);
    }

    default String findDocumentUrl(Vehicle vehicle, VehicleDocumentType type) {
        if (vehicle == null || vehicle.getDocuments() == null) {
            return null;
        }
        String url = null;
        for (VehicleDocument doc : vehicle.getDocuments()) {
            if (doc.getDocumentType() == type) {
                url = doc.getDocumentUrl();
            }
        }
        return url;
    }
}
