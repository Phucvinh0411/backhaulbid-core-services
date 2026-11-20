package iuh.fit.se.contractservice.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryProofResponse(
        UUID id,
        UUID tripId,
        String imageUrl,
        String note,
        Instant uploadedAt
) {
}
