package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateAuctionAwardRequest(
        @NotBlank String auctionId,
        @NotBlank String awardAttemptId,
        @NotBlank String winningBidId,
        @NotNull UUID shipperId,
        @NotNull UUID carrierId,
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 500) String pickupLocation,
        @NotBlank @Size(max = 500) String deliveryLocation,
        @NotNull @PositiveOrZero BigDecimal agreedPrice,
        Instant expectedDeliveryAt,
        String depositHoldId,
        @PositiveOrZero BigDecimal depositAmount,
        @NotNull Instant signingDeadlineAt,
        @Valid TripRoutePoint pickupPoint,
        @Valid TripRoutePoint deliveryPoint
) {
    public CreateAuctionAwardRequest(String auctionId, String awardAttemptId, String winningBidId,
            UUID shipperId, UUID carrierId, UUID vehicleId, String pickupLocation, String deliveryLocation,
            BigDecimal agreedPrice, Instant expectedDeliveryAt, String depositHoldId, BigDecimal depositAmount,
            Instant signingDeadlineAt) {
        this(auctionId, awardAttemptId, winningBidId, shipperId, carrierId, vehicleId, pickupLocation,
                deliveryLocation, agreedPrice, expectedDeliveryAt, depositHoldId, depositAmount,
                signingDeadlineAt, null, null);
    }
    public CreateAuctionAwardRequest(
            String auctionId,
            String winningBidId,
            UUID shipperId,
            UUID carrierId,
            UUID vehicleId,
            String pickupLocation,
            String deliveryLocation,
            BigDecimal agreedPrice,
            Instant expectedDeliveryAt,
            String depositHoldId,
            BigDecimal depositAmount) {
        this(auctionId, auctionId + ":" + winningBidId, winningBidId, shipperId, carrierId,
                vehicleId, pickupLocation, deliveryLocation, agreedPrice, expectedDeliveryAt,
                depositHoldId, depositAmount, Instant.now().plusSeconds(86_400), null, null);
    }
}
