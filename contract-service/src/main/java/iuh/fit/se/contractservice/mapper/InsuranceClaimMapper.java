package iuh.fit.se.contractservice.mapper;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;
import iuh.fit.se.contractservice.dto.CargoDocumentResponse;
import iuh.fit.se.contractservice.dto.CarrierClaimResponse;
import iuh.fit.se.contractservice.dto.ClaimEvidenceResponse;
import iuh.fit.se.contractservice.dto.HandoverContextResponse;
import iuh.fit.se.contractservice.dto.InsuranceClaimResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InsuranceClaimMapper {

    @Mapping(target = "fileLink", expression = "java(buildCargoDocumentFileLink(document))")
    CargoDocumentResponse toCargoDocumentResponse(TripCargoDocument document);

    List<CargoDocumentResponse> toCargoDocumentResponses(List<TripCargoDocument> documents);

    @Mapping(target = "id", source = "file.id")
    @Mapping(target = "kind", source = "file.kind")
    @Mapping(target = "uploadedAt", source = "file.uploadedAt")
    @Mapping(target = "fileLink", expression = "java(buildEvidenceFileLink(claimId, file))")
    ClaimEvidenceResponse toClaimEvidenceResponse(UUID claimId, InsuranceClaimEvidence file);

    @Mapping(target = "id", source = "claim.id")
    @Mapping(target = "tripId", source = "claim.tripId")
    @Mapping(target = "incidentType", source = "claim.incidentType")
    @Mapping(target = "occurredAt", source = "claim.occurredAt")
    @Mapping(target = "description", source = "claim.description")
    @Mapping(target = "claimedAmount", source = "claim.claimedAmount")
    @Mapping(target = "status", source = "claim.status")
    @Mapping(target = "providerReference", source = "claim.providerReference")
    @Mapping(target = "revision", source = "claim.revision")
    @Mapping(target = "sharedRevision", source = "claim.sharedRevision")
    @Mapping(target = "sharedAt", source = "claim.sharedAt")
    @Mapping(target = "carrierAcknowledgedRevision", source = "claim.carrierAcknowledgedRevision")
    @Mapping(target = "carrierAcknowledgedAt", source = "claim.carrierAcknowledgedAt")
    @Mapping(target = "shareStale", expression = "java(isShareStale(claim))")
    @Mapping(target = "canShare", expression = "java(canShareClaim(claim, missingItems))")
    @Mapping(target = "missingItems", source = "missingItems")
    @Mapping(target = "declaredGoodsValue", source = "declaredGoodsValue")
    @Mapping(target = "cargoDocuments", source = "cargoDocuments")
    @Mapping(target = "handover", source = "handover")
    @Mapping(target = "evidence", expression = "java(toClaimEvidenceResponses(claim.getId(), files))")
    @Mapping(target = "packageLink", expression = "java(buildClaimPackageLink(claim.getId()))")
    @Mapping(target = "createdAt", source = "claim.createdAt")
    @Mapping(target = "updatedAt", source = "claim.updatedAt")
    InsuranceClaimResponse toInsuranceClaimResponse(
            InsuranceClaim claim,
            List<InsuranceClaimEvidence> files,
            List<String> missingItems,
            BigDecimal declaredGoodsValue,
            List<CargoDocumentResponse> cargoDocuments,
            HandoverContextResponse handover);

    CarrierClaimResponse.CargoItem toCarrierCargoItem(TripCargoDocument document);

    CarrierClaimResponse.EvidenceItem toCarrierEvidenceItem(InsuranceClaimEvidence file);

    @Mapping(target = "id", source = "claim.id")
    @Mapping(target = "tripId", source = "claim.tripId")
    @Mapping(target = "incidentType", source = "claim.incidentType")
    @Mapping(target = "occurredAt", source = "claim.occurredAt")
    @Mapping(target = "description", source = "claim.description")
    @Mapping(target = "claimedAmount", source = "claim.claimedAmount")
    @Mapping(target = "status", source = "claim.status")
    @Mapping(target = "sharedRevision", source = "claim.sharedRevision")
    @Mapping(target = "sharedAt", source = "claim.sharedAt")
    @Mapping(target = "carrierAcknowledgedAt", source = "claim.carrierAcknowledgedAt")
    @Mapping(target = "acknowledged", expression = "java(isCarrierAcknowledged(claim))")
    @Mapping(target = "cargoDocuments", source = "cargo")
    @Mapping(target = "handover", source = "handover")
    @Mapping(target = "evidence", source = "files")
    @Mapping(target = "packageLink", expression = "java(buildClaimPackageLink(claim.getId()))")
    CarrierClaimResponse toCarrierClaimResponse(
            InsuranceClaim claim,
            List<TripCargoDocument> cargo,
            List<InsuranceClaimEvidence> files,
            HandoverContextResponse handover);

    default String buildCargoDocumentFileLink(TripCargoDocument document) {
        return "/api/v1/trips/" + document.getTripId() + "/cargo-documents/" + document.getId() + "/file";
    }

    default String buildEvidenceFileLink(UUID claimId, InsuranceClaimEvidence file) {
        return "/api/v1/claims/" + claimId + "/evidence/" + file.getId() + "/file";
    }

    default List<ClaimEvidenceResponse> toClaimEvidenceResponses(UUID claimId, List<InsuranceClaimEvidence> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream().map(file -> toClaimEvidenceResponse(claimId, file)).toList();
    }

    default String buildClaimPackageLink(UUID claimId) {
        return "/api/v1/claims/" + claimId + "/package";
    }

    default boolean isShareStale(InsuranceClaim claim) {
        return claim.getSharedRevision() != null && claim.getSharedRevision() < claim.getRevision();
    }

    default boolean canShareClaim(InsuranceClaim claim, List<String> missingItems) {
        return claim.getStatus() == InsuranceClaimStatus.READY_TO_SHARE
                && (missingItems == null || missingItems.isEmpty());
    }

    default boolean isCarrierAcknowledged(InsuranceClaim claim) {
        return claim.getCarrierAcknowledgedRevision() != null
                && claim.getCarrierAcknowledgedRevision().equals(claim.getSharedRevision());
    }
}
