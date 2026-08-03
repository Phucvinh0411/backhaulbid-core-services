package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.Company;
import iuh.fit.se.domain.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByAccount_Id(UUID accountId);
    boolean existsByTaxCodeAndAccount_IdNot(String taxCode, UUID accountId);
    Page<Company> findAllByVerificationStatus(
            VerificationStatus status, Pageable pageable);
}
