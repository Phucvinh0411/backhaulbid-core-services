package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * One value document as the auction saved it, sent by bidding-service with each award attempt. The object key is
 * kept on the trip for the private proxy only; no response returns it.
 */
public record CargoValueDocumentRef(
        @NotBlank @Size(max = 36) String documentId,
        @NotNull CargoDocumentKind kind,
        @NotBlank @Size(max = 500) String objectKey,
        @NotNull UUID uploadedBy,
        @NotNull Instant uploadedAt
) {
}
