package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {
}
