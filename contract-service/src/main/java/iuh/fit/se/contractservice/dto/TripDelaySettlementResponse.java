package iuh.fit.se.contractservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripDelaySettlementResponse(
        UUID id,
        UUID tripId,
        String auctionId,
        UUID shipperId,
        UUID carrierId,
        int tier,
        long lateMinutes,
        int cumulativePenaltyPercent,
        BigDecimal totalCompensationAmount,
        BigDecimal incrementalCompensationAmount,
        int pointsDeducted,
        String walletStatus,
        String reputationStatus,
        String notificationStatus,
        String overallStatus,
        String lastError,
        int attempts,
        Instant updatedAt
) {
}
