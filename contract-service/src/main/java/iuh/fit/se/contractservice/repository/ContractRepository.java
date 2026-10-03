package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByTripId(UUID tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c join fetch c.trip where c.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c join fetch c.trip where c.trip.id = :tripId")
    Optional<Contract> findByTripIdForUpdate(@Param("tripId") UUID tripId);

    Optional<Contract> findByContractCode(String contractCode);

    List<Contract> findByCarrierIdOrderByCreatedAtDesc(UUID carrierId);

    List<Contract> findByShipperIdOrderByCreatedAtDesc(UUID shipperId);

    List<Contract> findAllByOrderByCreatedAtDesc();
}
