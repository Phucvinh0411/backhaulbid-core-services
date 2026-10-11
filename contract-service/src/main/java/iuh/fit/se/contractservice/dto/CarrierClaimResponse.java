package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentSource;
import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A shared claim as the trip's carrier sees it. Shown only after the shipper shared it, and only the revision that was
 * shared: later evidence does not appear until the shipper shares again. It lists what the pack contains (kinds and
 * times), not file links; the files come with the package download.
 */
public record CarrierClaimResponse(
        UUID id,
        UUID tripId,
        ClaimIncidentType incidentType,
        Instant occurredAt,
        String description,
        BigDecimal claimedAmount,
        InsuranceClaimStatus status,
        int sharedRevision,
        Instant sharedAt,
        Instant carrierAcknowledgedAt,
        boolean acknowledged,
        List<CargoItem> cargoDocuments,
        HandoverContextResponse handover,
        List<EvidenceItem> evidence,
        String packageLink
) {
    public record CargoItem(CargoDocumentKind kind, CargoDocumentSource source, Instant uploadedAt) {
    }

    public record EvidenceItem(ClaimEvidenceKind kind, Instant uploadedAt) {
    }

    public static CarrierClaimResponse from(InsuranceClaim claim, List<TripCargoDocument> cargo,
                                            List<InsuranceClaimEvidence> files, HandoverContextResponse handover) {
        return new CarrierClaimResponse(claim.getId(), claim.getTripId(), claim.getIncidentType(), claim.getOccurredAt(),
                claim.getDescription(), claim.getClaimedAmount(), claim.getStatus(), claim.getSharedRevision(),
                claim.getSharedAt(), claim.getCarrierAcknowledgedAt(),
                claim.getCarrierAcknowledgedRevision() != null
                        && claim.getCarrierAcknowledgedRevision().equals(claim.getSharedRevision()),
                cargo.stream().map(row -> new CargoItem(row.getKind(), row.getSource(), row.getUploadedAt())).toList(),
                handover,
                files.stream().map(file -> new EvidenceItem(file.getKind(), file.getUploadedAt())).toList(),
                "/api/v1/claims/" + claim.getId() + "/package");
    }
}
