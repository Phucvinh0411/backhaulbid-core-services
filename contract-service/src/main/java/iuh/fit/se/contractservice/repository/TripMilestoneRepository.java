package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripMilestoneRepository extends JpaRepository<TripMilestone, UUID> {

    List<TripMilestone> findByTripIdOrderBySequenceOrderAsc(UUID tripId);

    Optional<TripMilestone> findByIdAndTripId(UUID milestoneId, UUID tripId);
}
