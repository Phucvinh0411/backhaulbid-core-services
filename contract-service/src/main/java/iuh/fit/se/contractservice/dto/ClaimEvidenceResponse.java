package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
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
    public static ClaimEvidenceResponse from(UUID claimId, InsuranceClaimEvidence file) {
        return new ClaimEvidenceResponse(file.getId(), file.getKind(), file.getUploadedAt(),
                "/api/v1/claims/" + claimId + "/evidence/" + file.getId() + "/file");
    }
}
