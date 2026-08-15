package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
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
