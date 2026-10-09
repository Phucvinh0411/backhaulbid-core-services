package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripRoutePointAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TripRoutePointAuditRepository extends JpaRepository<TripRoutePointAudit, UUID> {}
