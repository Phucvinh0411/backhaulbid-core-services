package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Trip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.Instant;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    @Override
    @EntityGraph(attributePaths = {"contract", "trackingLogs"})
    Optional<Trip> findById(UUID id);

    Optional<Trip> findByAuctionId(String auctionId);

    Optional<Trip> findByAwardAttemptId(String awardAttemptId);

    @Query("select t.id from Trip t where t.driverId = :profile and t.status not in :closed")
    List<UUID> findIdsByDriverIdAndStatusNotIn(@Param("profile") UUID profile,
                                              @Param("closed") java.util.Collection<iuh.fit.se.contractservice.domain.enums.TripStatus> closed);

    @Query(value = "select t.* from trips t where " +
            "exists (select 1 from contracts c where c.trip_id = t.id and c.status = 'SIGNED') and " +
            "((t.expected_delivery_at < :now and t.deposit_hold_id is not null and t.deposit_amount > 0 and " +
            "(t.status not in ('CANCELLED', 'COMPLETED') or exists (select 1 from trip_delay_settlements s " +
            "where s.trip_id = t.id and (s.wallet_status <> 'COMPLETED' or s.reputation_status <> 'COMPLETED' " +
            "or s.notification_status <> 'COMPLETED')))) or " +
            "(t.deposit_hold_id is not null and t.deposit_released_at is null and t.status in ('CANCELLED', 'COMPLETED'))) ",
            nativeQuery = true)
    List<Trip> findTripsForLateReview(@Param("now") Instant now);

    @Query("select distinct t from Trip t left join fetch t.contract left join fetch t.trackingLogs where t.carrierId = :carrierId order by t.createdAt desc")
    List<Trip> findByCarrierIdOrderByCreatedAtDesc(@Param("carrierId") UUID carrierId);

    @Query("select distinct t from Trip t left join fetch t.contract left join fetch t.trackingLogs where t.driverAccountId = :driverAccountId order by t.createdAt desc")
    List<Trip> findByDriverAccountIdOrderByCreatedAtDesc(@Param("driverAccountId") UUID driverAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

    @Query("select distinct t from Trip t left join fetch t.contract left join fetch t.trackingLogs where t.shipperId = :shipperId order by t.createdAt desc")
    List<Trip> findByShipperIdOrderByCreatedAtDesc(@Param("shipperId") UUID shipperId);

    @Query("select distinct t from Trip t left join fetch t.contract left join fetch t.trackingLogs order by t.createdAt desc")
    List<Trip> findAllByOrderByCreatedAtDesc();
}
