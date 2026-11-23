package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripHandover;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripHandoverRepository extends JpaRepository<TripHandover, UUID> {
    @EntityGraph(attributePaths = {"photoKeys"})
    Optional<TripHandover> findByTripId(UUID tripId);
}
