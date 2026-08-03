package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {
}
