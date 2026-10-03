package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.Instant;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findByAuctionId(String auctionId);

    Optional<Trip> findByAwardAttemptId(String awardAttemptId);

    @Query(value = "select t.* from trips t where " +
            "((t.expected_delivery_at < :now and t.deposit_hold_id is not null and t.deposit_amount > 0 and " +
            "(t.status not in ('CANCELLED', 'COMPLETED') or exists (select 1 from trip_delay_settlements s " +
            "where s.trip_id = t.id and (s.wallet_status <> 'COMPLETED' or s.reputation_status <> 'COMPLETED' " +
            "or s.notification_status <> 'COMPLETED')))) or " +
            "(t.deposit_hold_id is not null and t.deposit_released_at is null and t.status in ('CANCELLED', 'COMPLETED'))) ",
            nativeQuery = true)
    List<Trip> findTripsForLateReview(@Param("now") Instant now);

    List<Trip> findByCarrierIdOrderByCreatedAtDesc(UUID carrierId);

    List<Trip> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    List<Trip> findByShipperIdOrderByCreatedAtDesc(UUID shipperId);

    List<Trip> findAllByOrderByCreatedAtDesc();
}
