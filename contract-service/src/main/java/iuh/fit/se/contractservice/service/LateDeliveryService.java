package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripDelaySettlement;
import iuh.fit.se.contractservice.dto.TripDelaySettlementResponse;
import iuh.fit.se.contractservice.repository.TripDelaySettlementRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LateDeliveryService {
    private static final int[] CUMULATIVE_PERCENT = {0, 5, 50, 100};
    private static final int[] POINTS_BY_TIER = {0, 5, 5, 10};

    private final TripRepository tripRepository;
    private final TripDelaySettlementRepository settlementRepository;
    private final LateDeliveryWalletClient walletClient;
    private final CarrierReputationClient reputationClient;
    private final LateDeliveryNotificationClient notificationClient;

    public void processTrip(Trip trip) {
        if (!isLatePolicyEnabled(trip) || trip.getExpectedDeliveryAt() == null) return;
        Instant checkedAt = trip.getDeliveredAt() == null ? Instant.now() : trip.getDeliveredAt();
        Duration lateness = Duration.between(trip.getExpectedDeliveryAt(), checkedAt);
        if (lateness.isNegative() || lateness.isZero()) return;
        long lateMinutes = Math.max(1, lateness.toMinutes());
        int lastDueTier = lateness.compareTo(Duration.ofHours(1)) < 0 ? 1
                : lateness.compareTo(Duration.ofHours(3)) <= 0 ? 2 : 3;
        for (int tier = 1; tier <= lastDueTier; tier++) {
            TripDelaySettlement settlement = getOrCreate(trip, tier, lateMinutes);
            processSettlement(trip, settlement);
        }
    }

    public void processTripAndReleaseIfClosed(Trip trip) {
        processTrip(trip);
        releaseRemainingDepositIfClosed(trip);
    }

    public void releaseRemainingDepositIfClosed(Trip trip) {
        boolean closed = trip.getStatus() == iuh.fit.se.contractservice.domain.enums.TripStatus.COMPLETED
                || trip.getStatus() == iuh.fit.se.contractservice.domain.enums.TripStatus.CANCELLED;
        if (!closed || trip.getDepositHoldId() == null || trip.getDepositReleasedAt() != null) return;
        List<TripDelaySettlement> settlements = settlementRepository.findByTripIdOrderByTierAsc(trip.getId());
        if (settlements.stream().anyMatch(settlement -> !"COMPLETED".equals(settlement.getWalletStatus()))) return;
        boolean fullyConsumed = settlements.stream().anyMatch(settlement -> settlement.getTier() == 3
                && "COMPLETED".equals(settlement.getWalletStatus()));
        try {
            if (!fullyConsumed) walletClient.release(trip.getDepositHoldId(), trip.getId());
            trip.setDepositReleasedAt(Instant.now());
            tripRepository.save(trip);
        } catch (RuntimeException exception) {
            log.warn("Could not release remaining deposit for trip {}: {}", trip.getId(), exception.getMessage());
        }
    }

    public List<TripDelaySettlement> getSettlements(UUID tripId) {
        return settlementRepository.findByTripIdOrderByTierAsc(tripId);
    }

    @Transactional(readOnly = true)
    public List<TripDelaySettlement> listForAdmin() {
        return settlementRepository.findAllByOrderByUpdatedAtDesc();
    }

    public void notifyLateCancellation(Trip trip) {
        try {
            notificationClient.notifyCancelled(trip.getShipperId(), trip.getId());
            notificationClient.notifyCancelled(trip.getCarrierId(), trip.getId());
        } catch (RuntimeException exception) {
            log.warn("Could not send late-cancellation notification for trip {}: {}", trip.getId(), exception.getMessage());
        }
    }

    public boolean isLatePolicyEnabled(Trip trip) {
        return trip.getExpectedDeliveryAt() != null && trip.getDepositHoldId() != null
                && trip.getDepositAmount() != null && trip.getDepositAmount().signum() > 0;
    }

    private TripDelaySettlement getOrCreate(Trip trip, int tier, long lateMinutes) {
        return settlementRepository.findByTripIdAndTier(trip.getId(), tier).orElseGet(() -> {
            int percent = CUMULATIVE_PERCENT[tier];
            BigDecimal deposit = trip.getDepositAmount();
            BigDecimal total = deposit.multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal previousTotal = deposit.multiply(BigDecimal.valueOf(CUMULATIVE_PERCENT[tier - 1]))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal increment = total.subtract(previousTotal).max(BigDecimal.ZERO);
            return settlementRepository.saveAndFlush(TripDelaySettlement.builder()
                    .trip(trip)
                    .tier(tier)
                    .lateMinutes(lateMinutes)
                    .cumulativePenaltyPercent(percent)
                    .totalCompensationAmount(total)
                    .incrementalCompensationAmount(increment)
                    .pointsDeducted(POINTS_BY_TIER[tier])
                    .walletStatus(increment.signum() == 0 ? "COMPLETED" : "PENDING")
                    .build());
        });
    }

    private void processSettlement(Trip trip, TripDelaySettlement settlement) {
        if ("COMPLETED".equals(settlement.getWalletStatus())
                && "COMPLETED".equals(settlement.getReputationStatus())
                && "COMPLETED".equals(settlement.getNotificationStatus())) {
            return;
        }
        settlement.setAttempts(settlement.getAttempts() + 1);
        List<String> errors = new ArrayList<>();
        if (!"COMPLETED".equals(settlement.getWalletStatus())) {
            try {
                walletClient.settle(trip.getDepositHoldId(), trip.getShipperId(),
                        settlement.getIncrementalCompensationAmount(), trip.getId(), settlement.getTier());
                settlement.setWalletStatus("COMPLETED");
            } catch (RuntimeException exception) {
                settlement.setWalletStatus("FAILED");
                errors.add("Ví: " + exception.getMessage());
            }
        }
        if (!"COMPLETED".equals(settlement.getReputationStatus())) {
            try {
                reputationClient.deductLateDeliveryPoints(trip.getCarrierId(), trip.getId(), settlement.getTier());
                settlement.setReputationStatus("COMPLETED");
            } catch (RuntimeException exception) {
                settlement.setReputationStatus("FAILED");
                errors.add("Điểm uy tín: " + exception.getMessage());
            }
        }
        if (!"COMPLETED".equals(settlement.getNotificationStatus())) {
            try {
                notificationClient.notify(trip.getShipperId(), trip.getId(), settlement.getTier(), settlement.getLateMinutes(), "SHIPPER");
                notificationClient.notify(trip.getCarrierId(), trip.getId(), settlement.getTier(), settlement.getLateMinutes(), "CARRIER");
                settlement.setNotificationStatus("COMPLETED");
            } catch (RuntimeException exception) {
                settlement.setNotificationStatus("FAILED");
                errors.add("Thông báo: " + exception.getMessage());
            }
        }
        boolean complete = "COMPLETED".equals(settlement.getWalletStatus())
                && "COMPLETED".equals(settlement.getReputationStatus())
                && "COMPLETED".equals(settlement.getNotificationStatus());
        settlement.setOverallStatus(complete ? "COMPLETED" : errors.isEmpty() ? "PENDING" : "FAILED");
        settlement.setLastError(errors.isEmpty() ? null : String.join("; ", errors).substring(0,
                Math.min(1000, String.join("; ", errors).length())));
        settlementRepository.save(settlement);
    }
}
