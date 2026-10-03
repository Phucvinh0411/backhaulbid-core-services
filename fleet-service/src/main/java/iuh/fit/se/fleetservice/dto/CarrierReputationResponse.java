package iuh.fit.se.fleetservice.dto;

import java.util.UUID;

public record CarrierReputationResponse(UUID carrierId, int score) {
}
