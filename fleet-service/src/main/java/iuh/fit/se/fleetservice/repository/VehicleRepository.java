package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    @Override
    @EntityGraph(attributePaths = {"verification", "documents"})
    Optional<Vehicle> findById(UUID id);

    long countByCarrierId(UUID carrierId);

    long countByCarrierIdAndStatus(UUID carrierId, VehicleStatus status);

    @Query(value = "SELECT status, COUNT(*) FROM vehicles GROUP BY status", nativeQuery = true)
    java.util.List<Object[]> countAllByStatus();

    @Query(value = "SELECT COUNT(DISTINCT carrier_id) FROM vehicles", nativeQuery = true)
    long countDistinctCarriers();

    Optional<Vehicle> findByLicensePlate(String licensePlate);

    Optional<Vehicle> findByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    @Query("select distinct v from Vehicle v left join fetch v.verification left join fetch v.documents where v.carrierId = :carrierId order by v.licensePlate asc")
    List<Vehicle> findByCarrierIdOrderByLicensePlateAsc(@Param("carrierId") UUID carrierId);

    @Query("select distinct v from Vehicle v left join fetch v.verification left join fetch v.documents where v.status = :status order by v.licensePlate asc")
    List<Vehicle> findByStatusOrderByLicensePlateAsc(@Param("status") VehicleStatus status);

    @Query("select distinct v from Vehicle v left join fetch v.verification left join fetch v.documents where v.carrierId = :carrierId and v.status in :statuses order by v.licensePlate asc")
    List<Vehicle> findByCarrierIdAndStatusInOrderByLicensePlateAsc(
            @Param("carrierId") UUID carrierId,
            @Param("statuses") Collection<VehicleStatus> statuses
    );
}
