package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.VehicleVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VehicleVerificationRepository extends JpaRepository<VehicleVerification, UUID> {
}
