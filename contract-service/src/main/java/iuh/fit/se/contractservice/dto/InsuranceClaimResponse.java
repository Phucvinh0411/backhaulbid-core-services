package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A claim draft as the shipper sees it: status, what is still missing, and evidence as proxy links. Never the
 * stored file URL, and never a provider reference that the provider did not confirm.
 */
public record InsuranceClaimResponse(
        UUID id,
        UUID tripId,
        ClaimIncidentType incidentType,
        Instant occurredAt,
        String description,
        BigDecimal claimedAmount,
        InsuranceClaimStatus status,
        String providerReference,
        List<String> missingItems,
        List<ClaimEvidenceResponse> evidence,
        Instant createdAt,
        Instant updatedAt
) {
    public static InsuranceClaimResponse from(InsuranceClaim claim, List<InsuranceClaimEvidence> files, List<String> missing) {
        return new InsuranceClaimResponse(claim.getId(), claim.getTripId(), claim.getIncidentType(), claim.getOccurredAt(),
                claim.getDescription(), claim.getClaimedAmount(), claim.getStatus(), claim.getProviderReference(), missing,
                files.stream().map(file -> ClaimEvidenceResponse.from(claim.getId(), file)).toList(),
                claim.getCreatedAt(), claim.getUpdatedAt());
    }
}
