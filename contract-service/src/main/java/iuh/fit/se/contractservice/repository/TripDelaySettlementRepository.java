package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripDelaySettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripDelaySettlementRepository extends JpaRepository<TripDelaySettlement, UUID> {
    Optional<TripDelaySettlement> findByTripIdAndTier(UUID tripId, int tier);

    List<TripDelaySettlement> findByTripIdOrderByTierAsc(UUID tripId);

    List<TripDelaySettlement> findAllByOrderByUpdatedAtDesc();
}
