package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;

import java.time.Instant;
import java.util.UUID;

/** One evidence file. The link is this service's own proxy; the storage URL is never included. */
public record ClaimEvidenceResponse(
        UUID id,
        ClaimEvidenceKind kind,
        Instant uploadedAt,
        String fileLink
) {
}
