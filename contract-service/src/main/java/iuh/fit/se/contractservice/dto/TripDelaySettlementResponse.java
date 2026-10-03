package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripDelaySettlement;

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
    public static TripDelaySettlementResponse from(TripDelaySettlement settlement) {
        return new TripDelaySettlementResponse(settlement.getId(), settlement.getTrip().getId(),
                settlement.getTrip().getAuctionId(), settlement.getTrip().getShipperId(), settlement.getTrip().getCarrierId(),
                settlement.getTier(), settlement.getLateMinutes(), settlement.getCumulativePenaltyPercent(),
                settlement.getTotalCompensationAmount(), settlement.getIncrementalCompensationAmount(),
                settlement.getPointsDeducted(), settlement.getWalletStatus(), settlement.getReputationStatus(),
                settlement.getNotificationStatus(), settlement.getOverallStatus(), settlement.getLastError(),
                settlement.getAttempts(), settlement.getUpdatedAt());
    }
}
