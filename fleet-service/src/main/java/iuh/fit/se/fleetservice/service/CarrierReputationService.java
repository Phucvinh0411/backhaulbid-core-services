package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.CarrierReputation;
import iuh.fit.se.fleetservice.domain.entity.CarrierReputationEntry;
import iuh.fit.se.fleetservice.dto.CarrierReputationEntryResponse;
import iuh.fit.se.fleetservice.dto.CarrierReputationHistoryResponse;
import iuh.fit.se.fleetservice.dto.CarrierReputationResponse;
import iuh.fit.se.fleetservice.repository.CarrierReputationEntryRepository;
import iuh.fit.se.fleetservice.repository.CarrierReputationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarrierReputationService {
    private static final int[] POINTS_BY_TIER = {0, 5, 5, 10};

    private final CarrierReputationRepository reputationRepository;
    private final CarrierReputationEntryRepository entryRepository;

    @Transactional(readOnly = true)
    public CarrierReputationResponse getScore(UUID carrierId) {
        if (carrierId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrier ID is required");
        }
        CarrierReputation reputation = reputationRepository.findById(carrierId)
                .orElseGet(() -> CarrierReputation.builder().carrierId(carrierId).score(100).build());
        return toResponse(reputation);
    }

    @Transactional(readOnly = true)
    public CarrierReputationHistoryResponse getHistory(UUID carrierId) {
        CarrierReputation reputation = reputationRepository.findById(carrierId)
                .orElseGet(() -> CarrierReputation.builder().carrierId(carrierId).score(100).build());
        List<CarrierReputationEntryResponse> history = entryRepository
                .findByCarrierIdOrderByCreatedAtDesc(carrierId)
                .stream()
                .map(CarrierReputationEntryResponse::from)
                .toList();
        return new CarrierReputationHistoryResponse(carrierId, reputation.getScore(), history);
    }

    @Transactional
    public CarrierReputationResponse applyLateDeliveryTier(UUID carrierId, UUID tripId, int tier) {
        if (tier < 1 || tier > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Late delivery tier must be between 1 and 3");
        }

        CarrierReputation reputation = ensureInitialized(carrierId);
        if (entryRepository.existsByTripIdAndTier(tripId, tier)) {
            return toResponse(reputation);
        }

        int pointsToDeduct = POINTS_BY_TIER[tier];
        int nextScore = Math.max(0, reputation.getScore() - pointsToDeduct);
        reputation.setScore(nextScore);
        reputationRepository.saveAndFlush(reputation);
        entryRepository.save(CarrierReputationEntry.builder()
                .carrierId(carrierId)
                .tripId(tripId)
                .tier(tier)
                .pointsDelta(-pointsToDeduct)
                .scoreAfter(nextScore)
                .reason("Giao hàng trễ bậc " + tier)
                .build());
        return toResponse(reputation);
    }

    private CarrierReputation ensureInitialized(UUID carrierId) {
        if (carrierId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrier ID is required");
        }
        reputationRepository.createDefaultIfMissing(carrierId);
        return reputationRepository.findByCarrierIdForUpdate(carrierId)
                .orElseThrow(() -> new IllegalStateException("Carrier reputation could not be initialized"));
    }

    private CarrierReputationResponse toResponse(CarrierReputation reputation) {
        return new CarrierReputationResponse(reputation.getCarrierId(), reputation.getScore());
    }
}
