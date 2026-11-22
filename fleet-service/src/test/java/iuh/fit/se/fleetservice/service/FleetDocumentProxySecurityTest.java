package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType;
import iuh.fit.se.fleetservice.repository.DriverProfileRepository;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import iuh.fit.se.fleetservice.repository.VehicleVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetDocumentProxySecurityTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private VehicleVerificationRepository verificationRepository;
    @Mock private DriverProfileRepository driverRepository;
    @Mock private ContractDriverAccessClient driverAccess;

    private VehicleService vehicleService;
    private DriverProfileService driverService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, verificationRepository);
        driverService = new DriverProfileService(driverRepository, driverAccess);
    }

    @Test
    void vehicleDocumentAllowsOwnerCarrierAndAdminAndRejectsOthers() {
        UUID ownerId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        String expectedUrl = "https://backhaulbid-s3.s3.ap-southeast-2.amazonaws.com/vehicles/registration/" + UUID.randomUUID() + ".png";
        Vehicle vehicle = Vehicle.builder()
                .id(vehicleId)
                .carrierId(ownerId)
                .documents(List.of(VehicleDocument.builder()
                        .documentType(VehicleDocumentType.REGISTRATION)
                        .documentUrl(expectedUrl)
                        .build()))
                .build();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThat(vehicleService.getAuthorizedDocumentUrl(ownerId, "CARRIER", vehicleId, "registration"))
                .isEqualTo(expectedUrl);
        assertThat(vehicleService.getAuthorizedDocumentUrl(UUID.randomUUID(), "ADMIN", vehicleId, "registration"))
                .isEqualTo(expectedUrl);

        assertThatThrownBy(() -> vehicleService.getAuthorizedDocumentUrl(UUID.randomUUID(), "CARRIER", vehicleId, "registration"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> vehicleService.getAuthorizedDocumentUrl(ownerId, "SHIPPER", vehicleId, "registration"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> vehicleService.getAuthorizedDocumentUrl(ownerId, "CARRIER", vehicleId, "other"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void driverLicenseDocumentAllowsOwnerCarrierAndAdminAndRejectsOtherCarrier() {
        UUID ownerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        String licenseUrl = "https://backhaulbid-s3.s3.ap-southeast-2.amazonaws.com/drivers/license/" + UUID.randomUUID() + ".jpg";
        DriverProfile driver = DriverProfile.builder()
                .id(driverId)
                .carrierId(ownerId)
                .licenseImageUrl(licenseUrl)
                .build();
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        assertThat(driverService.getAuthorizedLicenseImageUrl(ownerId, "CARRIER", driverId)).isEqualTo(licenseUrl);
        assertThat(driverService.getAuthorizedLicenseImageUrl(UUID.randomUUID(), "ADMIN", driverId)).isEqualTo(licenseUrl);
        assertThatThrownBy(() -> driverService.getAuthorizedLicenseImageUrl(UUID.randomUUID(), "CARRIER", driverId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
