package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    long countByCarrierId(UUID carrierId);

    long countByCarrierIdAndStatus(UUID carrierId, VehicleStatus status);

    @org.springframework.data.jpa.repository.Query(value = "SELECT status, COUNT(*) FROM vehicles GROUP BY status", nativeQuery = true)
    java.util.List<Object[]> countAllByStatus();

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(DISTINCT carrier_id) FROM vehicles", nativeQuery = true)
    long countDistinctCarriers();
    Optional<Vehicle> findByLicensePlate(String licensePlate);

    Optional<Vehicle> findByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    List<Vehicle> findByCarrierIdOrderByLicensePlateAsc(UUID carrierId);

    List<Vehicle> findByStatusOrderByLicensePlateAsc(VehicleStatus status);

    List<Vehicle> findByCarrierIdAndStatusInOrderByLicensePlateAsc(
            UUID carrierId,
            Collection<VehicleStatus> statuses
    );
}
