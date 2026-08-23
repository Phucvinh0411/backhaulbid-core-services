package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripLocationUpdateRepository extends JpaRepository<TripLocationUpdate, UUID> {
    List<TripLocationUpdate> findByTripIdOrderByRecordedAtAsc(UUID tripId);
    TripLocationUpdate findFirstByTripIdOrderByRecordedAtDesc(UUID tripId);
}
