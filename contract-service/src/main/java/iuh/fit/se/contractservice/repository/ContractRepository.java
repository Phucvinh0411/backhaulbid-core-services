package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Contract;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    @Override
    @EntityGraph(attributePaths = {"trip", "signatures"})
    Optional<Contract> findById(UUID id);

    Optional<Contract> findByTripId(UUID tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c join fetch c.trip where c.id = :id")
    Optional<Contract> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c join fetch c.trip where c.trip.id = :tripId")
    Optional<Contract> findByTripIdForUpdate(@Param("tripId") UUID tripId);

    Optional<Contract> findByContractCode(String contractCode);

    @Query("select distinct c from Contract c left join fetch c.trip left join fetch c.signatures where c.carrierId = :carrierId order by c.createdAt desc")
    List<Contract> findByCarrierIdOrderByCreatedAtDesc(@Param("carrierId") UUID carrierId);

    @Query("select distinct c from Contract c left join fetch c.trip left join fetch c.signatures where c.shipperId = :shipperId order by c.createdAt desc")
    List<Contract> findByShipperIdOrderByCreatedAtDesc(@Param("shipperId") UUID shipperId);

    @Query("select distinct c from Contract c left join fetch c.trip left join fetch c.signatures order by c.createdAt desc")
    List<Contract> findAllByOrderByCreatedAtDesc();
}
