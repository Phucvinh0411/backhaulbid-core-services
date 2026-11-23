package iuh.fit.se.contractservice.mapper;

import iuh.fit.se.contractservice.domain.entity.DeliveryProof;
import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import iuh.fit.se.contractservice.domain.entity.TrackingLog;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripDelaySettlement;
import iuh.fit.se.contractservice.domain.entity.TripHandover;
import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import iuh.fit.se.contractservice.domain.entity.TripReview;
import iuh.fit.se.contractservice.domain.entity.TripRouteLocation;
import iuh.fit.se.contractservice.domain.entity.TripTrackingSession;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CarrierReviewItemResponse;
import iuh.fit.se.contractservice.dto.DeliveryProofResponse;
import iuh.fit.se.contractservice.dto.HandoverContextResponse;
import iuh.fit.se.contractservice.dto.JourneyEventResponse;
import iuh.fit.se.contractservice.dto.TrackingResponse;
import iuh.fit.se.contractservice.dto.TripDelaySettlementResponse;
import iuh.fit.se.contractservice.dto.TripHandoverResponse;
import iuh.fit.se.contractservice.dto.TripLocationResponse;
import iuh.fit.se.contractservice.dto.TripResponse;
import iuh.fit.se.contractservice.dto.TripReviewResponse;
import iuh.fit.se.contractservice.dto.TripRoutePoint;
import iuh.fit.se.contractservice.service.TripTrackingService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TripMapper {

    TripRoutePoint toRoutePoint(TripRouteLocation location);

    TrackingResponse toTrackingResponse(TrackingLog trackingLog);

    @Mapping(target = "hasAssignmentPin", expression = "java(hasActiveAssignmentPin(trip))")
    @Mapping(target = "latePolicyEnabled", expression = "java(isLatePolicyEnabled(trip))")
    @Mapping(target = "lateMinutes", expression = "java(computeLateMinutes(trip))")
    @Mapping(target = "canCancelForLateDelivery", expression = "java(canCancelForLateDelivery(trip))")
    @Mapping(target = "latestTracking", expression = "java(toLatestTracking(trip))")
    @Mapping(target = "driverConnected", expression = "java(isDriverConnected(trip))")
    @Mapping(target = "driverName", ignore = true)
    @Mapping(target = "vehiclePlate", ignore = true)
    TripResponse toResponse(Trip trip);

    List<TripResponse> toResponses(List<Trip> trips);

    @Mapping(target = "tripId", source = "trip.id")
    JourneyEventResponse toJourneyEventResponse(JourneyEvent event);

    List<JourneyEventResponse> toJourneyEventResponses(List<JourneyEvent> events);

    @Mapping(target = "tripId", source = "trip.id")
    DeliveryProofResponse toDeliveryProofResponse(DeliveryProof proof);

    List<DeliveryProofResponse> toDeliveryProofResponses(List<DeliveryProof> proofs);

    @Mapping(target = "tripId", source = "trip.id")
    TripLocationResponse toLocationResponse(TripLocationUpdate location);

    List<TripLocationResponse> toLocationResponses(List<TripLocationUpdate> locations);

    @Mapping(target = "photoPaths", expression = "java(buildHandoverPhotoPaths(handover))")
    @Mapping(target = "readyForPickup", expression = "java(handover.isReadyForPickup())")
    TripHandoverResponse toHandoverResponse(TripHandover handover);

    List<TripHandoverResponse> toHandoverResponses(List<TripHandover> handovers);

    @Mapping(target = "recordedAt", source = "handover.recordedAt")
    @Mapping(target = "recordedByType", source = "handover.recordedByType")
    @Mapping(target = "cargoCategory", source = "handover.cargoCategory")
    @Mapping(target = "packageCount", source = "handover.packageCount")
    @Mapping(target = "grossWeightKg", source = "handover.grossWeightKg")
    @Mapping(target = "conditionStatus", source = "handover.conditionStatus")
    @Mapping(target = "conditionNote", source = "handover.conditionNote")
    @Mapping(target = "sealNumber", source = "handover.sealNumber")
    @Mapping(target = "placeNote", source = "handover.placeNote")
    @Mapping(target = "shipperConfirmedAt", source = "handover.shipperConfirmedAt")
    @Mapping(target = "photoCount", expression = "java(countHandoverPhotos(handover))")
    @Mapping(target = "photoLinks", expression = "java(buildHandoverPhotoLinks(handover, tripId, withPhotoLinks))")
    HandoverContextResponse toHandoverContextResponse(TripHandover handover, UUID tripId, boolean withPhotoLinks);

    TripReviewResponse toReviewResponse(TripReview review);

    List<TripReviewResponse> toReviewResponses(List<TripReview> reviews);

    CarrierReviewItemResponse toCarrierReviewItem(TripReview review);

    List<CarrierReviewItemResponse> toCarrierReviewItems(List<TripReview> reviews);

    @Mapping(target = "tripId", source = "trip.id")
    @Mapping(target = "auctionId", source = "trip.auctionId")
    @Mapping(target = "shipperId", source = "trip.shipperId")
    @Mapping(target = "carrierId", source = "trip.carrierId")
    TripDelaySettlementResponse toDelaySettlementResponse(TripDelaySettlement settlement);

    List<TripDelaySettlementResponse> toDelaySettlementResponses(List<TripDelaySettlement> settlements);

    TripTrackingService.Session toTrackingSession(TripTrackingSession session);

    default boolean hasActiveAssignmentPin(Trip trip) {
        return trip.getAssignmentPinHash() != null
                && trip.getAssignmentPinExpiresAt() != null
                && trip.getAssignmentPinExpiresAt().isAfter(Instant.now())
                && trip.getAssignmentPinAttempts() < 5;
    }

    default boolean isLatePolicyEnabled(Trip trip) {
        return trip.getExpectedDeliveryAt() != null
                && trip.getDepositHoldId() != null
                && trip.getDepositAmount() != null
                && trip.getDepositAmount().signum() > 0;
    }

    default long computeLateMinutes(Trip trip) {
        Instant referenceTime = trip.getDeliveredAt() == null ? Instant.now() : trip.getDeliveredAt();
        Duration lateness = trip.getExpectedDeliveryAt() == null
                ? Duration.ZERO
                : Duration.between(trip.getExpectedDeliveryAt(), referenceTime);
        return lateness.isNegative() || lateness.isZero()
                ? 0
                : Math.max(1, lateness.toMinutes());
    }

    default boolean canCancelForLateDelivery(Trip trip) {
        return isLatePolicyEnabled(trip)
                && trip.getDeliveredAt() == null
                && trip.getStatus() != TripStatus.CANCELLED
                && trip.getStatus() != TripStatus.COMPLETED
                && trip.getExpectedDeliveryAt().plus(Duration.ofHours(1)).isBefore(Instant.now());
    }

    default TrackingResponse toLatestTracking(Trip trip) {
        if (trip.getTrackingLogs() == null || trip.getTrackingLogs().isEmpty()) {
            return null;
        }
        TrackingLog latest = trip.getTrackingLogs().get(trip.getTrackingLogs().size() - 1);
        return latest == null ? null : toTrackingResponse(latest);
    }

    default boolean isDriverConnected(Trip trip) {
        return trip.getDriverSessionId() != null || trip.getDriverAccountId() != null;
    }

    default List<String> buildHandoverPhotoPaths(TripHandover handover) {
        return buildHandoverPhotoLinks(handover, handover.getTripId(), true);
    }

    default int countHandoverPhotos(TripHandover handover) {
        return handover.getPhotoKeys() == null ? 0 : handover.getPhotoKeys().size();
    }

    default List<String> buildHandoverPhotoLinks(TripHandover handover, UUID tripId, boolean withPhotoLinks) {
        if (!withPhotoLinks || handover.getPhotoKeys() == null || handover.getPhotoKeys().isEmpty()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>(handover.getPhotoKeys().size());
        for (int index = 0; index < handover.getPhotoKeys().size(); index++) {
            paths.add("/api/v1/trips/" + tripId + "/handover/photos/" + index);
        }
        return List.copyOf(paths);
    }
}
