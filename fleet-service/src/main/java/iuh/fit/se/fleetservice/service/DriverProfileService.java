package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.dto.CreateDriverRequest;
import iuh.fit.se.fleetservice.dto.ReviewDecision;
import iuh.fit.se.fleetservice.dto.ReviewVerificationRequest;
import iuh.fit.se.fleetservice.dto.UpdateDriverRequest;
import iuh.fit.se.fleetservice.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverProfileService {
    private final DriverProfileRepository driverRepository;

    @Transactional(readOnly = true)
    public List<DriverProfile> listMine(UUID carrierId, VerificationStatus status) {
        List<DriverProfile> drivers = driverRepository.findByCarrierIdOrderByFullNameAsc(carrierId);
        return status == null ? drivers : drivers.stream()
                .filter(driver -> driver.getStatus() == status)
                .toList();
    }

    @Transactional
    public DriverProfile create(UUID carrierId, CreateDriverRequest request) {
        String licenseNumber = normalize(request.licenseNumber());
        ensureLicenseAvailable(licenseNumber, null);
        return driverRepository.save(DriverProfile.builder()
                .carrierId(carrierId)
                .fullName(request.fullName().trim())
                .phone(request.phone().trim())
                .licenseNumber(licenseNumber)
                .licenseImageUrl(normalizeNullable(request.licenseImageUrl()))
                .status(VerificationStatus.PENDING)
                .build());
    }

    @Transactional
    public List<DriverProfile> createBulk(UUID carrierId, List<CreateDriverRequest> requests) {
        validateBulkSize(requests);
        return requests.stream().map(request -> create(carrierId, request)).toList();
    }

    @Transactional
    public DriverProfile update(UUID carrierId, UUID driverId, UpdateDriverRequest request) {
        DriverProfile driver = findOwned(carrierId, driverId);
        String licenseNumber = normalize(request.licenseNumber());
        ensureLicenseAvailable(licenseNumber, driverId);
        String imageUrl = normalizeNullable(request.licenseImageUrl());
        boolean changed = !driver.getFullName().equals(request.fullName().trim())
                || !driver.getPhone().equals(request.phone().trim())
                || !driver.getLicenseNumber().equals(licenseNumber)
                || !safeEquals(driver.getLicenseImageUrl(), imageUrl);

        driver.setFullName(request.fullName().trim());
        driver.setPhone(request.phone().trim());
        driver.setLicenseNumber(licenseNumber);
        driver.setLicenseImageUrl(imageUrl);
        if (changed && driver.getStatus() == VerificationStatus.VERIFIED) {
            driver.submitVerification();
        }
        return driverRepository.save(driver);
    }

    @Transactional
    public void delete(UUID carrierId, UUID driverId) {
        driverRepository.delete(findOwned(carrierId, driverId));
    }

    @Transactional(readOnly = true)
    public List<DriverProfile> listForReview(VerificationStatus status) {
        return driverRepository.findByStatusOrderByFullNameAsc(status);
    }

    @Transactional
    public DriverProfile review(UUID adminId, UUID driverId, ReviewVerificationRequest request) {
        DriverProfile driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
        if (request.decision() == ReviewDecision.REJECT && (request.reason() == null || request.reason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        if (request.decision() == ReviewDecision.APPROVE) {
            driver.approveBy(adminId);
        } else {
            driver.rejectBy(adminId, request.reason().trim());
        }
        return driverRepository.save(driver);
    }

    private DriverProfile findOwned(UUID carrierId, UUID driverId) {
        DriverProfile driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
        if (!carrierId.equals(driver.getCarrierId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver does not belong to the carrier");
        }
        return driver;
    }

    private void ensureLicenseAvailable(String licenseNumber, UUID currentId) {
        if (!driverRepository.existsByLicenseNumberIgnoreCase(licenseNumber)) {
            return;
        }
        driverRepository.findByLicenseNumberIgnoreCase(licenseNumber).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "License number already exists");
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
