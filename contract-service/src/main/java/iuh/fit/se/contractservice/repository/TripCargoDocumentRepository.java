package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripCargoDocumentRepository extends JpaRepository<TripCargoDocument, UUID> {
    List<TripCargoDocument> findByTripIdOrderByUploadedAtAscIdAsc(UUID tripId);

    Optional<TripCargoDocument> findByIdAndTripId(UUID id, UUID tripId);

    boolean existsByTripIdAndObjectKey(UUID tripId, String objectKey);

    long countByTripId(UUID tripId);
}
