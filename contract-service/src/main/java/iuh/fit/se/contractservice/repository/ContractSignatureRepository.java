package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, UUID> {
    Optional<ContractSignature> findByContractIdAndAccountId(UUID contractId, UUID accountId);
}
