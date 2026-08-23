package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import iuh.fit.se.fleetservice.domain.entity.VehicleVerification;
import iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VehicleType;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.dto.CreateVehicleRequest;
import iuh.fit.se.fleetservice.dto.ReviewDecision;
import iuh.fit.se.fleetservice.dto.ReviewVerificationRequest;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import iuh.fit.se.fleetservice.repository.VehicleVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleVerificationRepository verificationRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private UUID carrierId;
    private UUID adminId;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        carrierId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .carrierId(carrierId)
                .licensePlate("51H-12345")
                .vehicleType(VehicleType.TRUCK_MEDIUM)
                .payloadCapacity(new java.math.BigDecimal("1000.0"))
                .status(VehicleStatus.PENDING)
                .documents(new ArrayList<>())
                .build();
    }

    /**
     * Feature: Carrier manages vehicles
     * Scenario: Carrier creates a new vehicle
     * Given a carrier and a valid vehicle creation request
     * When the carrier creates the vehicle
     * Then the vehicle is saved with PENDING status and documents are attached
     */
    @Test
    void givenValidRequest_whenCreateVehicle_thenSaveAndReturnPendingVehicle() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                "51H-12345", VehicleType.TRUCK_MEDIUM, "Box", new java.math.BigDecimal("1000.0"), "http://reg.url", "http://insp.url"
        );
        when(vehicleRepository.existsByLicensePlateIgnoreCase("51H-12345")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle created = vehicleService.create(carrierId, request);

        assertEquals("51H-12345", created.getLicensePlate());
        assertEquals(VehicleStatus.PENDING, created.getStatus());
        assertEquals(2, created.getDocuments().size());
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    /**
     * Feature: Admin reviews vehicles
     * Scenario: Admin approves a pending vehicle
     * Given an admin and a pending vehicle
     * When the admin reviews with APPROVE decision
     * Then the vehicle status becomes VERIFIED and verification is saved
     */
    @Test
    void givenPendingVehicle_whenAdminApproves_thenSetStatusToVerified() {
        ReviewVerificationRequest request = new ReviewVerificationRequest(ReviewDecision.APPROVE, null);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle reviewed = vehicleService.review(adminId, vehicle.getId(), request);

        assertEquals(VehicleStatus.VERIFIED, reviewed.getStatus());
        assertNotNull(reviewed.getVerification());
        assertEquals(VerificationStatus.VERIFIED, reviewed.getVerification().getStatus());
        verify(verificationRepository).save(any(VehicleVerification.class));
    }

    /**
     * Feature: Admin reviews vehicles
     * Scenario: Admin rejects a pending vehicle
     * Given an admin and a pending vehicle
     * When the admin reviews with REJECT decision and a reason
     * Then the vehicle status becomes REJECTED and verification saves the reason
     */
    @Test
    void givenPendingVehicle_whenAdminRejects_thenSetStatusToRejected() {
        ReviewVerificationRequest request = new ReviewVerificationRequest(ReviewDecision.REJECT, "Missing pages");
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle reviewed = vehicleService.review(adminId, vehicle.getId(), request);

        assertEquals(VehicleStatus.REJECTED, reviewed.getStatus());
        assertNotNull(reviewed.getVerification());
        assertEquals(VerificationStatus.REJECTED, reviewed.getVerification().getStatus());
        assertEquals("Missing pages", reviewed.getVerification().getNote());
        verify(verificationRepository).save(any(VehicleVerification.class));
    }
}
