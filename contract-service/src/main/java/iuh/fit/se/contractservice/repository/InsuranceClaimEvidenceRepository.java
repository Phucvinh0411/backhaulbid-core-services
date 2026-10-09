package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceClaimEvidenceRepository extends JpaRepository<InsuranceClaimEvidence, UUID> {
    List<InsuranceClaimEvidence> findByClaimIdOrderByUploadedAtAsc(UUID claimId);

    long countByClaimId(UUID claimId);

    Optional<InsuranceClaimEvidence> findByIdAndClaimId(UUID id, UUID claimId);
}
