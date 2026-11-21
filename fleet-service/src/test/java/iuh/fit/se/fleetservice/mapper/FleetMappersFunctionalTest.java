package iuh.fit.se.fleetservice.mapper;

import iuh.fit.se.fleetservice.domain.entity.CarrierReputation;
import iuh.fit.se.fleetservice.domain.entity.CarrierReputationEntry;
import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import iuh.fit.se.fleetservice.domain.entity.VehicleVerification;
import iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.dto.CarrierReputationEntryResponse;
import iuh.fit.se.fleetservice.dto.CarrierReputationResponse;
import iuh.fit.se.fleetservice.dto.DriverResponse;
import iuh.fit.se.fleetservice.dto.PublicDriverResponse;
import iuh.fit.se.fleetservice.dto.PublicVehicleResponse;
import iuh.fit.se.fleetservice.dto.VehicleResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FleetMappersFunctionalTest {

    private final VehicleMapper vehicleMapper = Mappers.getMapper(VehicleMapper.class);
    private final DriverMapper driverMapper = Mappers.getMapper(DriverMapper.class);
    private final CarrierReputationMapper reputationMapper = Mappers.getMapper(CarrierReputationMapper.class);

    @Test
    void vehicleMapperExtractsDocumentsAndVerificationDetails() {
        UUID vehicleId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder()
                .id(vehicleId)
                .carrierId(carrierId)
                .licensePlate("51C-888.99")
                .vehicleType(VehicleType.TRUCK_HEAVY)
                .bodyType("Thùng kín")
                .payloadCapacity(new BigDecimal("15000.00"))
                .status(VehicleStatus.VERIFIED)
                .verification(VehicleVerification.builder()
                        .status(VerificationStatus.VERIFIED)
                        .note("Hồ sơ hợp lệ")
                        .build())
                .documents(List.of(
                        VehicleDocument.builder()
                                .documentType(VehicleDocumentType.REGISTRATION)
                                .documentUrl("/media/reg.pdf")
                                .build(),
                        VehicleDocument.builder()
                                .documentType(VehicleDocumentType.INSPECTION_CERTIFICATE)
                                .documentUrl("/media/insp.pdf")
                                .build()))
                .build();

        VehicleResponse response = vehicleMapper.toResponse(vehicle);
        assertThat(response.id()).isEqualTo(vehicleId);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(response.rejectionReason()).isEqualTo("Hồ sơ hợp lệ");
        assertThat(response.registrationDocumentUrl()).isEqualTo("/media/reg.pdf");
        assertThat(response.inspectionDocumentUrl()).isEqualTo("/media/insp.pdf");

        PublicVehicleResponse publicView = vehicleMapper.toPublicResponse(vehicle);
        assertThat(publicView.id()).isEqualTo(vehicleId);
        assertThat(publicView.licensePlate()).isEqualTo("51C-888.99");
    }

    @Test
    void driverAndCarrierReputationMappersMapAllFields() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = DriverProfile.builder()
                .id(driverId)
                .fullName("Le Van Tai Xe")
                .phone("0905111222")
                .licenseNumber("FC-998877")
                .licenseImageUrl("/media/license.png")
                .status(VerificationStatus.VERIFIED)
                .build();

        DriverResponse driverResponse = driverMapper.toResponse(driver);
        assertThat(driverResponse.id()).isEqualTo(driverId);
        assertThat(driverResponse.fullName()).isEqualTo("Le Van Tai Xe");

        PublicDriverResponse publicDriver = driverMapper.toPublicResponse(driver);
        assertThat(publicDriver.licenseNumber()).isEqualTo("FC-998877");

        UUID carrierId = UUID.randomUUID();
        CarrierReputation reputation = CarrierReputation.builder().carrierId(carrierId).score(95).build();
        CarrierReputationResponse repResponse = reputationMapper.toResponse(reputation);
        assertThat(repResponse.carrierId()).isEqualTo(carrierId);
        assertThat(repResponse.score()).isEqualTo(95);

        CarrierReputationEntry entry = CarrierReputationEntry.builder()
                .tripId(UUID.randomUUID())
                .tier(1)
                .pointsDelta(-5)
                .scoreAfter(95)
                .reason("Giao hàng trễ bậc 1")
                .createdAt(Instant.now())
                .build();
        List<CarrierReputationEntryResponse> entries = reputationMapper.toEntryResponses(List.of(entry));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).pointsDelta()).isEqualTo(-5);
    }
}
