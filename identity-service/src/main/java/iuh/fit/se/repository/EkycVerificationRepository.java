package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.EkycVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EkycVerificationRepository extends JpaRepository<EkycVerification, UUID> {
    Optional<EkycVerification> findByAccount_Id(UUID accountId);
}
