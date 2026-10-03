package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByCarrierIdOrderByCreatedAtDesc(UUID carrierId);

    List<Trip> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    List<Trip> findByShipperIdOrderByCreatedAtDesc(UUID shipperId);

    List<Trip> findAllByOrderByCreatedAtDesc();
}
