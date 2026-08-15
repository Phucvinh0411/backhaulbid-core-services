package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    Optional<DriverProfile> findByLicenseNumberIgnoreCase(String licenseNumber);

    List<DriverProfile> findByCarrierIdOrderByFullNameAsc(UUID carrierId);

    List<DriverProfile> findByStatusOrderByFullNameAsc(VerificationStatus status);
}
