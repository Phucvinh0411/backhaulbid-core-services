package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, UUID> {
    List<InsuranceClaim> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    /** Claim row lock, so readiness checks and evidence uploads for one claim cannot interleave. */
    Optional<InsuranceClaim> findByIdAndShipperId(UUID id, UUID shipperId);
}
