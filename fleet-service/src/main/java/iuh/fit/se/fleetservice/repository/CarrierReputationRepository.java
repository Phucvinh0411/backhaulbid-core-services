package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.CarrierReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierReputationRepository extends JpaRepository<CarrierReputation, UUID> {
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO carrier_reputations (carrier_id, score, version, created_at, updated_at) " +
            "VALUES (:carrierId, 100, 0, NOW(), NOW()) ON CONFLICT (carrier_id) DO NOTHING", nativeQuery = true)
    void createDefaultIfMissing(@Param("carrierId") UUID carrierId);

    @Query("select reputation from CarrierReputation reputation where reputation.carrierId = :carrierId")
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CarrierReputation> findByCarrierIdForUpdate(@Param("carrierId") UUID carrierId);
}
