package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripReviewRepository extends JpaRepository<TripReview, UUID> {
    Optional<TripReview> findByTripId(UUID tripId);

    Page<TripReview> findByCarrierId(UUID carrierId, Pageable pageable);

    /** Rows of {@code [rating (Integer), count (Long)]} for the carrier's reviews; absent stars are not returned. */
    @Query("select r.rating, count(r) from TripReview r where r.carrierId = :carrier group by r.rating")
    List<Object[]> distributionByCarrier(@Param("carrier") UUID carrier);

    /** Null when the carrier has no reviews. */
    @Query("select avg(r.rating) from TripReview r where r.carrierId = :carrier")
    Double averageByCarrier(@Param("carrier") UUID carrier);
}
