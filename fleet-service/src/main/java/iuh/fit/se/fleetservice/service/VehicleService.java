package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import iuh.fit.se.fleetservice.domain.entity.VehicleVerification;
import iuh.fit.se.fleetservice.domain.enums.VehicleDocumentType;
import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.dto.CreateVehicleRequest;
import iuh.fit.se.fleetservice.dto.ReviewDecision;
import iuh.fit.se.fleetservice.dto.ReviewVerificationRequest;
import iuh.fit.se.fleetservice.dto.UpdateVehicleRequest;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import iuh.fit.se.fleetservice.repository.VehicleVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final VehicleVerificationRepository verificationRepository;

    @Transactional(readOnly = true)
    public List<Vehicle> listMine(UUID carrierId, VehicleStatus status) {
        List<Vehicle> vehicles = vehicleRepository.findByCarrierIdOrderByLicensePlateAsc(carrierId);
        return status == null ? vehicles : vehicles.stream()
                .filter(vehicle -> vehicle.getStatus() == status)
                .toList();
    }

    @Transactional
    public Vehicle create(UUID carrierId, CreateVehicleRequest request) {
        String licensePlate = normalize(request.licensePlate());
        ensureLicensePlateAvailable(licensePlate, null);

        Vehicle vehicle = Vehicle.builder()
                .carrierId(carrierId)
                .licensePlate(licensePlate)
                .vehicleType(request.vehicleType())
                .bodyType(normalizeNullable(request.bodyType()))
                .payloadCapacity(request.payloadCapacity())
                .status(VehicleStatus.PENDING)
                .build();
        
        if (request.registrationUrl() != null && !request.registrationUrl().isBlank()) {
            vehicle.getDocuments().add(VehicleDocument.builder()
                    .vehicle(vehicle)
                    .documentType(VehicleDocumentType.REGISTRATION)
                    .documentUrl(request.registrationUrl())
                    .status(VerificationStatus.PENDING)
                    .build());
        }
        if (request.inspectionUrl() != null && !request.inspectionUrl().isBlank()) {
            vehicle.getDocuments().add(VehicleDocument.builder()
                    .vehicle(vehicle)
                    .documentType(VehicleDocumentType.INSPECTION_CERTIFICATE)
                    .documentUrl(request.inspectionUrl())
                    .status(VerificationStatus.PENDING)
                    .build());
        }
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public List<Vehicle> createBulk(UUID carrierId, List<CreateVehicleRequest> requests) {
        validateBulkSize(requests);
        return requests.stream().map(request -> create(carrierId, request)).toList();
    }

    @Transactional
    public Vehicle update(UUID carrierId, UUID vehicleId, UpdateVehicleRequest request) {
        Vehicle vehicle = findOwned(carrierId, vehicleId);
        String licensePlate = normalize(request.licensePlate());
        ensureLicensePlateAvailable(licensePlate, vehicleId);

        boolean changed = !licensePlate.equals(vehicle.getLicensePlate())
                || request.vehicleType() != vehicle.getVehicleType()
                || !request.payloadCapacity().equals(vehicle.getPayloadCapacity())
                || !safeEquals(request.bodyType(), vehicle.getBodyType());

        vehicle.setLicensePlate(licensePlate);
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setBodyType(normalizeNullable(request.bodyType()));
        vehicle.setPayloadCapacity(request.payloadCapacity());

        if (request.registrationUrl() != null && !request.registrationUrl().isBlank()) {
            vehicle.getDocuments().removeIf(d -> d.getDocumentType() == VehicleDocumentType.REGISTRATION);
            vehicle.getDocuments().add(VehicleDocument.builder()
                    .vehicle(vehicle)
                    .documentType(VehicleDocumentType.REGISTRATION)
                    .documentUrl(request.registrationUrl())
                    .status(VerificationStatus.PENDING)
                    .build());
            changed = true;
        }
        if (request.inspectionUrl() != null && !request.inspectionUrl().isBlank()) {
            vehicle.getDocuments().removeIf(d -> d.getDocumentType() == VehicleDocumentType.INSPECTION_CERTIFICATE);
            vehicle.getDocuments().add(VehicleDocument.builder()
                    .vehicle(vehicle)
                    .documentType(VehicleDocumentType.INSPECTION_CERTIFICATE)
                    .documentUrl(request.inspectionUrl())
                    .status(VerificationStatus.PENDING)
                    .build());
            changed = true;
        }

        if (changed && vehicle.getStatus() == VehicleStatus.VERIFIED) {
            vehicle.setStatus(VehicleStatus.PENDING);
        }
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public void deactivate(UUID carrierId, UUID vehicleId) {
        Vehicle vehicle = findOwned(carrierId, vehicleId);
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listForReview(VehicleStatus status) {
        return vehicleRepository.findByStatusOrderByLicensePlateAsc(status);
    }

    @Transactional
    public Vehicle review(UUID adminId, UUID vehicleId, ReviewVerificationRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (request.decision() == ReviewDecision.REJECT && (request.reason() == null || request.reason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }

        VehicleVerification verification = vehicle.getVerification();
        if (verification == null) {
            verification = VehicleVerification.builder()
                    .vehicle(vehicle)
                    .status(VerificationStatus.PENDING)
                    .build();
            vehicle.setVerification(verification);
        }

        if (request.decision() == ReviewDecision.APPROVE) {
            verification.approve(adminId);
            vehicle.setStatus(VehicleStatus.VERIFIED);
        } else {
            verification.reject(adminId, request.reason().trim());
            vehicle.setStatus(VehicleStatus.REJECTED);
        }
        verificationRepository.save(verification);
        return vehicleRepository.save(vehicle);
    }

    private Vehicle findOwned(UUID carrierId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!carrierId.equals(vehicle.getCarrierId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vehicle does not belong to the carrier");
        }
        return vehicle;
    }

    private void ensureLicensePlateAvailable(String licensePlate, UUID currentId) {
        if (!vehicleRepository.existsByLicensePlateIgnoreCase(licensePlate)) {
            return;
        }
        vehicleRepository.findByLicensePlateIgnoreCase(licensePlate).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "License plate already exists");
            }
        });
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private void validateBulkSize(List<?> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bulk import must contain 1 to 500 records");
        }
    }
}
