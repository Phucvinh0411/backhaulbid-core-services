package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.CarrierReputationEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarrierReputationEntryRepository extends JpaRepository<CarrierReputationEntry, UUID> {
    boolean existsByTripIdAndTier(UUID tripId, int tier);

    List<CarrierReputationEntry> findByCarrierIdOrderByCreatedAtDesc(UUID carrierId);
}
