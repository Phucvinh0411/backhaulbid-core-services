package iuh.fit.se.contractservice.mapper;

import iuh.fit.se.contractservice.domain.entity.Complaint;
import iuh.fit.se.contractservice.domain.entity.ComplaintMessage;
import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.entity.TrackingLog;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import iuh.fit.se.contractservice.domain.entity.TripDelaySettlement;
import iuh.fit.se.contractservice.domain.entity.TripHandover;
import iuh.fit.se.contractservice.domain.entity.TripRouteLocation;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentSource;
import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;
import iuh.fit.se.contractservice.domain.enums.ComplaintCategory;
import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CarrierClaimResponse;
import iuh.fit.se.contractservice.dto.ComplaintResponse;
import iuh.fit.se.contractservice.dto.ContractResponse;
import iuh.fit.se.contractservice.dto.HandoverContextResponse;
import iuh.fit.se.contractservice.dto.InsuranceClaimResponse;
import iuh.fit.se.contractservice.dto.TripDelaySettlementResponse;
import iuh.fit.se.contractservice.dto.TripHandoverResponse;
import iuh.fit.se.contractservice.dto.TripResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContractMappersFunctionalTest {

    private final TripMapper tripMapper = Mappers.getMapper(TripMapper.class);
    private final ContractMapper contractMapper = createContractMapper();
    private final ComplaintMapper complaintMapper = Mappers.getMapper(ComplaintMapper.class);
    private final InsuranceClaimMapper claimMapper = Mappers.getMapper(InsuranceClaimMapper.class);

    private ContractMapper createContractMapper() {
        ContractMapper mapper = Mappers.getMapper(ContractMapper.class);
        ReflectionTestUtils.setField(mapper, "tripMapper", tripMapper);
        return mapper;
    }

    @Test
    void tripAndContractMappersComputeDerivedLatenessPinAndSignatures() {
        UUID tripId = UUID.randomUUID();
        Instant now = Instant.now();
        TrackingLog log = TrackingLog.builder()
                .id(UUID.randomUUID())
                .latitude(10.77)
                .longitude(106.69)
                .status(TripStatus.IN_TRANSIT)
                .recordedAt(now)
                .build();
        Trip trip = Trip.builder()
                .id(tripId)
                .shipperId(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .driverSessionId(UUID.randomUUID())
                .assignmentPinHash("hash")
                .assignmentPinExpiresAt(now.plus(Duration.ofMinutes(15)))
                .assignmentPinAttempts(1)
                .auctionId("auc-1")
                .pickupLocation("TP.HCM")
                .deliveryLocation("Hà Nội")
                .agreedPrice(new BigDecimal("18000000.00"))
                .expectedDeliveryAt(now.minus(Duration.ofMinutes(90)))
                .depositHoldId("hold-1")
                .depositAmount(new BigDecimal("1800000.00"))
                .status(TripStatus.IN_TRANSIT)
                .pickupPoint(new TripRouteLocation(10.77, 106.69, "Kho A", "HCM", "USER_CONFIRMED"))
                .deliveryPoint(new TripRouteLocation(21.02, 105.83, "Kho B", "HN", "USER_CONFIRMED"))
                .trackingLogs(List.of(log))
                .build();

        TripResponse tripDto = tripMapper.toResponse(trip);
        assertThat(tripDto.id()).isEqualTo(tripId);
        assertThat(tripDto.hasAssignmentPin()).isTrue();
        assertThat(tripDto.driverConnected()).isTrue();
        assertThat(tripDto.latePolicyEnabled()).isTrue();
        assertThat(tripDto.lateMinutes()).isGreaterThanOrEqualTo(89);
        assertThat(tripDto.canCancelForLateDelivery()).isTrue();
        assertThat(tripDto.latestTracking()).isNotNull();
        assertThat(tripDto.pickupPoint().latitude()).isEqualTo(10.77);

        Contract contract = Contract.builder()
                .id(UUID.randomUUID())
                .trip(trip)
                .shipperId(trip.getShipperId())
                .carrierId(trip.getCarrierId())
                .contractCode("HD-2026-001")
                .status(ContractStatus.WAITING_SIGNATURE)
                .signatures(List.of(ContractSignature.builder()
                        .accountId(trip.getCarrierId())
                        .role(AccountRole.CARRIER)
                        .signedAt(now)
                        .build()))
                .build();

        ContractResponse contractDto = contractMapper.toResponse(contract);
        assertThat(contractDto.tripId()).isEqualTo(tripId);
        assertThat(contractDto.trip().id()).isEqualTo(tripId);
        assertThat(contractDto.signatures()).hasSize(1);
    }

    @Test
    void handoverDelaySettlementComplaintAndInsuranceClaimMappersBuildSafeLinks() {
        UUID tripId = UUID.randomUUID();
        Instant now = Instant.now();
        TripHandover handover = TripHandover.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .cargoCategory("Hàng điện tử")
                .packageCount(10)
                .grossWeightKg(new BigDecimal("1200.00"))
                .conditionStatus(TripHandover.GOOD)
                .recordedByType(TripHandover.RECORDED_BY_DRIVER)
                .recordedAt(now)
                .shipperConfirmedAt(now)
                .photoKeys(List.of("handover-photos/1.jpg", "handover-photos/2.jpg"))
                .build();

        TripHandoverResponse handoverDto = tripMapper.toHandoverResponse(handover);
        assertThat(handoverDto.readyForPickup()).isTrue();
        assertThat(handoverDto.photoPaths()).containsExactly(
                "/api/v1/trips/" + tripId + "/handover/photos/0",
                "/api/v1/trips/" + tripId + "/handover/photos/1");

        HandoverContextResponse shipperHandoverCtx = tripMapper.toHandoverContextResponse(handover, tripId, true);
        assertThat(shipperHandoverCtx.photoCount()).isEqualTo(2);
        assertThat(shipperHandoverCtx.photoLinks()).hasSize(2);

        HandoverContextResponse carrierHandoverCtx = tripMapper.toHandoverContextResponse(handover, tripId, false);
        assertThat(carrierHandoverCtx.photoCount()).isEqualTo(2);
        assertThat(carrierHandoverCtx.photoLinks()).isEmpty();

        Trip trip = Trip.builder()
                .id(tripId)
                .auctionId("auc-99")
                .shipperId(UUID.randomUUID())
                .carrierId(UUID.randomUUID())
                .build();
        TripDelaySettlement settlement = TripDelaySettlement.builder()
                .id(UUID.randomUUID())
                .trip(trip)
                .tier(2)
                .lateMinutes(180)
                .cumulativePenaltyPercent(30)
                .totalCompensationAmount(new BigDecimal("540000.00"))
                .incrementalCompensationAmount(new BigDecimal("360000.00"))
                .pointsDeducted(5)
                .build();
        TripDelaySettlementResponse settlementDto = tripMapper.toDelaySettlementResponse(settlement);
        assertThat(settlementDto.tripId()).isEqualTo(tripId);
        assertThat(settlementDto.auctionId()).isEqualTo("auc-99");

        Complaint complaint = Complaint.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .reporterId(trip.getShipperId())
                .respondentId(trip.getCarrierId())
                .category(ComplaintCategory.SCHEDULE_DELAY)
                .title("Giao hàng trễ")
                .description("Trễ 3 giờ")
                .status(ComplaintStatus.PENDING)
                .messages(List.of(ComplaintMessage.builder()
                        .id(UUID.randomUUID())
                        .senderId(trip.getShipperId())
                        .senderRole(AccountRole.SHIPPER)
                        .message("Yêu cầu kiểm tra")
                        .createdAt(now)
                        .build()))
                .build();
        ComplaintResponse complaintDto = complaintMapper.toResponse(complaint);
        assertThat(complaintDto.messages()).hasSize(1);

        UUID claimId = UUID.randomUUID();
        InsuranceClaim claim = InsuranceClaim.builder()
                .id(claimId)
                .tripId(tripId)
                .incidentType(ClaimIncidentType.DAMAGE)
                .occurredAt(now)
                .description("Móp thùng hàng")
                .claimedAmount(new BigDecimal("5000000.00"))
                .status(InsuranceClaimStatus.READY_TO_SHARE)
                .revision(2)
                .sharedRevision(1)
                .sharedAt(now)
                .carrierAcknowledgedRevision(1)
                .carrierAcknowledgedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        TripCargoDocument cargoDoc = TripCargoDocument.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .kind(CargoDocumentKind.INVOICE)
                .source(CargoDocumentSource.AWARD_SNAPSHOT)
                .objectKey("goods-value-docs/secret.pdf")
                .uploadedAt(now)
                .build();
        InsuranceClaimEvidence evidenceFile = InsuranceClaimEvidence.builder()
                .id(UUID.randomUUID())
                .claimId(claimId)
                .kind(ClaimEvidenceKind.PHOTO)
                .objectKey("claim-evidence/secret.jpg")
                .uploadedAt(now)
                .revision(1)
                .build();

        InsuranceClaimResponse shipperClaim = claimMapper.toInsuranceClaimResponse(
                claim,
                List.of(evidenceFile),
                List.of(),
                new BigDecimal("100000000.00"),
                claimMapper.toCargoDocumentResponses(List.of(cargoDoc)),
                shipperHandoverCtx);
        assertThat(shipperClaim.shareStale()).isTrue();
        assertThat(shipperClaim.canShare()).isTrue();
        assertThat(shipperClaim.packageLink()).isEqualTo("/api/v1/claims/" + claimId + "/package");
        assertThat(shipperClaim.evidence().get(0).fileLink())
                .isEqualTo("/api/v1/claims/" + claimId + "/evidence/" + evidenceFile.getId() + "/file");

        CarrierClaimResponse carrierClaim = claimMapper.toCarrierClaimResponse(
                claim,
                List.of(cargoDoc),
                List.of(evidenceFile),
                carrierHandoverCtx);
        assertThat(carrierClaim.acknowledged()).isTrue();
        assertThat(carrierClaim.cargoDocuments()).hasSize(1);
    }
}
