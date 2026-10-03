package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

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
        @NotBlank String pickupLocation,
        @NotBlank String deliveryLocation,
        @NotNull @PositiveOrZero BigDecimal agreedPrice,
        Instant expectedDeliveryAt,
        String depositHoldId,
        @PositiveOrZero BigDecimal depositAmount,
        @NotNull Instant signingDeadlineAt
) {
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
                depositHoldId, depositAmount, Instant.now().plusSeconds(86_400));
    }
}
