package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

public record TripResponse(
        UUID id,
        UUID shipperId,
        UUID carrierId,
        UUID vehicleId,
        UUID driverId,
        boolean hasAssignmentPin,
        String auctionId,
        String winningBidId,
        String pickupLocation,
        String deliveryLocation,
        BigDecimal agreedPrice,
        Instant expectedDeliveryAt,
        Instant deliveredAt,
        BigDecimal depositAmount,
        Instant depositReleasedAt,
        boolean latePolicyEnabled,
        long lateMinutes,
        boolean canCancelForLateDelivery,
        TripStatus status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        TrackingResponse latestTracking
) {
    public static TripResponse from(Trip trip) {
        Instant referenceTime = trip.getDeliveredAt() == null ? Instant.now() : trip.getDeliveredAt();
        Duration lateness = trip.getExpectedDeliveryAt() == null
                ? Duration.ZERO : Duration.between(trip.getExpectedDeliveryAt(), referenceTime);
        long lateMinutes = lateness.isNegative() || lateness.isZero()
                ? 0 : Math.max(1, lateness.toMinutes());
        boolean latePolicyEnabled = trip.getExpectedDeliveryAt() != null && trip.getDepositHoldId() != null
                && trip.getDepositAmount() != null && trip.getDepositAmount().signum() > 0;
        boolean canCancelForLateDelivery = latePolicyEnabled && trip.getDeliveredAt() == null
                && trip.getStatus() != iuh.fit.se.contractservice.domain.enums.TripStatus.CANCELLED
                && trip.getStatus() != iuh.fit.se.contractservice.domain.enums.TripStatus.COMPLETED
                && trip.getExpectedDeliveryAt().plus(Duration.ofHours(1)).isBefore(Instant.now());
        return new TripResponse(
                trip.getId(),
                trip.getShipperId(),
                trip.getCarrierId(),
                trip.getVehicleId(),
                trip.getDriverId(),
                trip.getAssignmentPinHash() != null,
                trip.getAuctionId(),
                trip.getWinningBidId(),
                trip.getPickupLocation(),
                trip.getDeliveryLocation(),
                trip.getAgreedPrice(),
                trip.getExpectedDeliveryAt(),
                trip.getDeliveredAt(),
                trip.getDepositAmount(),
                trip.getDepositReleasedAt(),
                latePolicyEnabled,
                lateMinutes,
                canCancelForLateDelivery,
                trip.getStatus(),
                trip.getCancellationReason(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                trip.getTrackingLogs() == null || trip.getTrackingLogs().isEmpty()
                        ? null
                        : trip.getTrackingLogs().get(trip.getTrackingLogs().size() - 1) == null
                        ? null
                        : TrackingResponse.from(trip.getTrackingLogs().get(trip.getTrackingLogs().size() - 1))
        );
    }
}
