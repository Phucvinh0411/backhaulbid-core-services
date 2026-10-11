package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, UUID> {
    List<InsuranceClaim> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    Optional<InsuranceClaim> findByIdAndShipperId(UUID id, UUID shipperId);

    /** Claims shared with this carrier, newest share first. A claim is never listed before its first share. */
    List<InsuranceClaim> findByCarrierIdAndSharedRevisionIsNotNullOrderBySharedAtDesc(UUID carrierId);

    /**
     * Row lock for share and acknowledgement, so two requests for one claim cannot both move its revision or status.
     * Taken after the trip is read, the same order the award and signing paths use.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InsuranceClaim c where c.id = :id")
    Optional<InsuranceClaim> findByIdForUpdate(@Param("id") UUID id);
}
