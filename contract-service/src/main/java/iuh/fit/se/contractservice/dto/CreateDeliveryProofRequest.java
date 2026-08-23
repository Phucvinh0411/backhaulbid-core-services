package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeliveryProofRequest(
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 500) String note
) {}
