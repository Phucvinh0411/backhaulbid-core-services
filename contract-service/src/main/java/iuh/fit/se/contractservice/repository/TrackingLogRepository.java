package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TrackingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TrackingLogRepository extends JpaRepository<TrackingLog, UUID> {
}
