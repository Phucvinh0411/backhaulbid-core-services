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
 * A claim draft as the shipper sees it. The three sources of documents are kept apart: the trip's order documents
 * (cargoDocuments), the pickup record (handover) and the incident evidence (evidence). Every file is a proxy link;
 * no storage key or URL is included, and a provider reference appears only if a provider confirmed one.
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
        int revision,
        Integer sharedRevision,
        Instant sharedAt,
        Integer carrierAcknowledgedRevision,
        Instant carrierAcknowledgedAt,
        boolean shareStale,
        boolean canShare,
        List<String> missingItems,
        BigDecimal declaredGoodsValue,
        List<CargoDocumentResponse> cargoDocuments,
        HandoverContextResponse handover,
        List<ClaimEvidenceResponse> evidence,
        String packageLink,
        Instant createdAt,
        Instant updatedAt
) {
    public static InsuranceClaimResponse from(InsuranceClaim claim, List<InsuranceClaimEvidence> files, List<String> missing,
                                              BigDecimal declaredGoodsValue, List<CargoDocumentResponse> cargoDocuments,
                                              HandoverContextResponse handover) {
        boolean stale = claim.getSharedRevision() != null && claim.getSharedRevision() < claim.getRevision();
        return new InsuranceClaimResponse(claim.getId(), claim.getTripId(), claim.getIncidentType(), claim.getOccurredAt(),
                claim.getDescription(), claim.getClaimedAmount(), claim.getStatus(), claim.getProviderReference(),
                claim.getRevision(), claim.getSharedRevision(), claim.getSharedAt(),
                claim.getCarrierAcknowledgedRevision(), claim.getCarrierAcknowledgedAt(), stale,
                claim.getStatus() == InsuranceClaimStatus.READY_TO_SHARE && missing.isEmpty(), missing,
                declaredGoodsValue, cargoDocuments, handover,
                files.stream().map(file -> ClaimEvidenceResponse.from(claim.getId(), file)).toList(),
                "/api/v1/claims/" + claim.getId() + "/package",
                claim.getCreatedAt(), claim.getUpdatedAt());
    }
}
