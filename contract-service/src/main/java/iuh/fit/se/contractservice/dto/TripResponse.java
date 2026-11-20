package iuh.fit.se.contractservice.dto;

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
        UUID driverAccountId,
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
        TrackingResponse latestTracking,
        TripRoutePoint pickupPoint,
        TripRoutePoint deliveryPoint,
        long routeVersion,
        // V3.1: true once the assigned driver signed in (code session or legacy account); no IDs exposed.
        boolean driverConnected,
        long assignmentVersion
) {
}
