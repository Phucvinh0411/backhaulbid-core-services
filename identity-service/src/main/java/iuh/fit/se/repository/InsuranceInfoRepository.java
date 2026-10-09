package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.InsuranceInfo;
import iuh.fit.se.domain.enums.InsuranceCoverageType;
import iuh.fit.se.domain.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceInfoRepository extends JpaRepository<InsuranceInfo, UUID> {
    List<InsuranceInfo> findAllByCompany_IdAndCoverageType(UUID companyId, InsuranceCoverageType coverageType);

    Optional<InsuranceInfo> findFirstByCompany_IdAndCoverageTypeOrderBySubmittedAtDesc(
            UUID companyId, InsuranceCoverageType coverageType);

    boolean existsByCompany_IdAndCoverageTypeAndStatus(
            UUID companyId, InsuranceCoverageType coverageType, VerificationStatus status);

    Page<InsuranceInfo> findAllByCoverageTypeAndStatus(
            InsuranceCoverageType coverageType, VerificationStatus status, Pageable pageable);
}
