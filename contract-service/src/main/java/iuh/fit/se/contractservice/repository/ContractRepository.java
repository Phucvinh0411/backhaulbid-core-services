package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByContractCode(String contractCode);

    List<Contract> findByCarrierIdOrderByCreatedAtDesc(UUID carrierId);

    List<Contract> findByShipperIdOrderByCreatedAtDesc(UUID shipperId);

    List<Contract> findAllByOrderByCreatedAtDesc();
}
