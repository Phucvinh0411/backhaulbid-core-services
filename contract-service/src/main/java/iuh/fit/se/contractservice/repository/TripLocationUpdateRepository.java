package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

public interface TripLocationUpdateRepository extends JpaRepository<TripLocationUpdate, UUID> {
    List<TripLocationUpdate> findByTripIdOrderByRecordedAtAsc(UUID tripId);
    TripLocationUpdate findFirstByTripIdOrderByRecordedAtDesc(UUID tripId);
    Optional<TripLocationUpdate> findByTrackingSessionIdAndSampleId(UUID trackingSessionId, UUID sampleId);
    @Query("select l from TripLocationUpdate l where l.trip.id=:trip order by l.ingestionSequence desc")
    List<TripLocationUpdate> recent(@Param("trip") UUID trip, Pageable limit);
    long countByTripId(UUID tripId);
    @Query(value="select sampled.* from (select l.*, row_number() over(order by ingestion_sequence) as rn, "
            + "count(*) over() as total from trip_location_updates l where trip_id=:trip) sampled "
            + "where rn=total or (:limit>1 and (rn=1 or mod(rn-1,greatest(ceil(cast(total-1 as decimal(38,10))/greatest(:limit-1,1)),1))=0)) "
            + "order by ingestion_sequence",nativeQuery=true)
    List<TripLocationUpdate> overview(@Param("trip") UUID trip,@Param("limit") int limit);
    @Query("select l from TripLocationUpdate l where l.trip.id=:trip and "
            + "l.ingestionSequence>:sequence order by l.ingestionSequence")
    List<TripLocationUpdate> after(@Param("trip") UUID trip, @Param("sequence") long sequence, Pageable limit);
    @Query("select l from TripLocationUpdate l where l.trip.id=:trip and "
            + "(l.actorId=:driver or (l.source=iuh.fit.se.contractservice.domain.entity.TripLocationSource.MANUAL and               (l.actorId=l.trip.carrierId or l.actorRole=iuh.fit.se.contractservice.domain.enums.AccountRole.ADMIN)) or               (l.source=iuh.fit.se.contractservice.domain.entity.TripLocationSource.CHECK_IN and l.actorRole=iuh.fit.se.contractservice.domain.enums.AccountRole.ADMIN)) "
            + "order by coalesce(l.capturedAt,l.recordedAt) desc,l.id desc")
    List<TripLocationUpdate> latestValid(@Param("trip") UUID trip, @Param("driver") UUID driver, Pageable limit);
    @Modifying @Query("delete from TripLocationUpdate l where l.source=iuh.fit.se.contractservice.domain.entity.TripLocationSource.GPS and l.recordedAt<:before")
    int deleteExpiredGps(@Param("before") Instant before);
}
