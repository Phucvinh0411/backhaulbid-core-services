package iuh.fit.se.fleetservice.dto;

import java.util.List;
import java.util.UUID;

public record CarrierReputationHistoryResponse(
        UUID carrierId,
        int score,
        List<CarrierReputationEntryResponse> history
) {
}
