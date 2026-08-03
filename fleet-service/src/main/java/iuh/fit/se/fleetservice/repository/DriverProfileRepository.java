package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
}
