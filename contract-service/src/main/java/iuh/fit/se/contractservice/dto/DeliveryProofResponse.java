package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.DeliveryProof;

import java.time.Instant;
import java.util.UUID;

public record DeliveryProofResponse(
        UUID id,
        UUID tripId,
        String imageUrl,
        String note,
        Instant uploadedAt
) {
    public static DeliveryProofResponse from(DeliveryProof proof) {
        return new DeliveryProofResponse(
                proof.getId(),
                proof.getTrip().getId(),
                proof.getImageUrl(),
                proof.getNote(),
                proof.getUploadedAt());
    }
}
