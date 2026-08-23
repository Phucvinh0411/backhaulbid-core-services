package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripResponse(
        UUID id,
        UUID shipperId,
        UUID carrierId,
        UUID vehicleId,
        UUID driverId,
        boolean hasAssignmentPin,
        String pickupLocation,
        String deliveryLocation,
        BigDecimal agreedPrice,
        TripStatus status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        TrackingResponse latestTracking
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getShipperId(),
                trip.getCarrierId(),
                trip.getVehicleId(),
                trip.getDriverId(),
                trip.getAssignmentPinHash() != null,
                trip.getPickupLocation(),
                trip.getDeliveryLocation(),
                trip.getAgreedPrice(),
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
