package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JourneyEventRepository extends JpaRepository<JourneyEvent, UUID> {
    List<JourneyEvent> findByTripIdOrderByRecordedAtAsc(UUID tripId);
}
