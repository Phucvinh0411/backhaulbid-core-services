package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.AddClaimEvidenceRequest;
import iuh.fit.se.contractservice.dto.CargoDocumentResponse;
import iuh.fit.se.contractservice.dto.CarrierClaimResponse;
import iuh.fit.se.contractservice.dto.CreateInsuranceClaimRequest;
import iuh.fit.se.contractservice.dto.HandoverContextResponse;
import iuh.fit.se.contractservice.dto.InsuranceClaimResponse;
import iuh.fit.se.contractservice.repository.InsuranceClaimEvidenceRepository;
import iuh.fit.se.contractservice.repository.InsuranceClaimRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.service.driveraccess.UuidV7;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Claim drafts for a trip's goods. The shipper opens a draft, attaches private evidence, and marks it ready to share
 * once the checklist is complete. Sharing hands the claim to the trip's carrier; the carrier confirms receipt. Nothing
 * here is sent to a provider, and no provider state is set. Evidence is append-only: after a share, new evidence is a
 * new revision that must be shared again, so the carrier's copy never changes underneath them.
 */
@Service
public class InsuranceClaimService {
    public static final int MAX_EVIDENCE = 20;
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    /** Goods are on the vehicle from pickup until the shipper accepted delivery. */
    private static final Set<TripStatus> CLAIMABLE = EnumSet.of(TripStatus.PICKED_UP, TripStatus.IN_TRANSIT,
            TripStatus.DELIVERED, TripStatus.COMPLETED);
    /** Statuses that still accept evidence. A shared claim accepts it as a new revision. */
    private static final Set<InsuranceClaimStatus> EVIDENCE_OPEN = EnumSet.of(InsuranceClaimStatus.DRAFT,
            InsuranceClaimStatus.NEEDS_EVIDENCE, InsuranceClaimStatus.READY_TO_SHARE,
            InsuranceClaimStatus.SHARED_WITH_CARRIER, InsuranceClaimStatus.CARRIER_ACKNOWLEDGED);
    /** Statuses that may be checked for readiness. */
    private static final Set<InsuranceClaimStatus> PREPARING = EnumSet.of(InsuranceClaimStatus.DRAFT,
            InsuranceClaimStatus.NEEDS_EVIDENCE, InsuranceClaimStatus.READY_TO_SHARE);
    private static final String EVIDENCE_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    /** Only files the shipper uploaded to the private claim-evidence folder can be attached. A URL keeps only its key. */
    private static final Pattern EVIDENCE_URL = Pattern.compile(
            "^https?://[^\\s]{1,400}/(claim-evidence/" + EVIDENCE_UUID + "\\.(png|jpe?g|webp|gif|pdf))$", Pattern.CASE_INSENSITIVE);
    /** The stored object key itself. */
    private static final Pattern EVIDENCE_KEY = Pattern.compile(
            "^(claim-evidence/" + EVIDENCE_UUID + "\\.(png|jpe?g|webp|gif|pdf))$", Pattern.CASE_INSENSITIVE);

    private final TripRepository trips;
    private final InsuranceClaimRepository claims;
    private final InsuranceClaimEvidenceRepository evidence;
    private final MediaEvidenceClient media;
    private final TripCargoDocumentService cargoDocuments;
    private final TripHandoverService handovers;
    private final ClaimPackageService packages;
    private final Clock clock;
    private final UuidV7 ids;

    public InsuranceClaimService(TripRepository trips, InsuranceClaimRepository claims, InsuranceClaimEvidenceRepository evidence,
                                 MediaEvidenceClient media, TripCargoDocumentService cargoDocuments,
                                 TripHandoverService handovers, ClaimPackageService packages, Clock clock) {
        this.trips = trips;
        this.claims = claims;
        this.evidence = evidence;
        this.media = media;
        this.cargoDocuments = cargoDocuments;
        this.handovers = handovers;
        this.packages = packages;
        this.clock = clock;
        this.ids = new UuidV7(clock, new SecureRandom());
    }

    /** A private evidence file read for the shipper or an admin. */
    public record EvidenceFile(byte[] bytes, String contentType) {
    }

    /** Opens a draft for a trip whose goods have been picked up. Only the trip's shipper may open one. */
    @Transactional
    public InsuranceClaimResponse create(UUID accountId, AccountRole role, UUID tripId, CreateInsuranceClaimRequest request) {
        requireShipper(role);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (!accountId.equals(trip.getShipperId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shipper of this trip can open a claim draft");
        }
        if (!CLAIMABLE.contains(trip.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ mở hồ sơ bồi thường khi hàng đã được lấy lên xe");
        }
        Instant now = clock.instant();
        if (request.occurredAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời điểm sự cố không được ở tương lai");
        }
        String description = request.description().trim();
        if (description.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mô tả sự cố cần ít nhất 10 ký tự");
        }
        InsuranceClaim claim = InsuranceClaim.builder()
                .id(ids.next())
                .tripId(tripId)
                .shipperId(trip.getShipperId())
                .carrierId(trip.getCarrierId())
                .incidentType(request.incidentType())
                .occurredAt(request.occurredAt())
                .description(description)
                .claimedAmount(request.claimedAmount() == null ? null : request.claimedAmount().setScale(2, RoundingMode.HALF_UP))
                .status(InsuranceClaimStatus.DRAFT)
                .revision(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return view(claims.saveAndFlush(claim));
    }

    /**
     * Attaches one already-uploaded private file. Before a share it joins the draft. After a share it starts a new
     * revision, and the claim waits for a new share; the shared copy is not changed.
     */
    @Transactional
    public InsuranceClaimResponse addEvidence(UUID accountId, AccountRole role, UUID claimId, AddClaimEvidenceRequest request) {
        requireShipper(role);
        InsuranceClaim claim = ownedClaimForUpdate(claimId, accountId);
        if (!EVIDENCE_OPEN.contains(claim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ này không còn nhận thêm chứng cứ");
        }
        String reference = request.fileUrl().trim();
        Matcher reader = EVIDENCE_KEY.matcher(reference);
        if (!reader.matches()) {
            reader = EVIDENCE_URL.matcher(reference);
        }
        if (!reader.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp chứng cứ không hợp lệ. Hãy tải lại tệp ảnh, PDF hoặc video phù hợp.");
        }
        String objectKey = reader.group(1);
        UUID claimIdValue = claim.getId();
        if (evidence.countByClaimId(claimIdValue) >= MAX_EVIDENCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ đã đủ số lượng chứng cứ tối đa");
        }
        Instant now = clock.instant();
        boolean shared = claim.getSharedRevision() != null;
        if (shared) {
            claim.setRevision(claim.getRevision() + 1);
            claim.setStatus(InsuranceClaimStatus.READY_TO_SHARE);
        }
        evidence.saveAndFlush(InsuranceClaimEvidence.builder()
                .id(ids.next())
                .claimId(claimIdValue)
                .kind(request.kind())
                .objectKey(objectKey)
                .uploadedBy(accountId)
                .uploadedAt(now)
                .revision(claim.getRevision())
                .build());
        claim.setUpdatedAt(now);
        return view(claims.saveAndFlush(claim));
    }

    /**
     * Checks the checklist. Complete: the draft becomes READY_TO_SHARE (still not sent to anyone). Incomplete: it becomes
     * NEEDS_EVIDENCE and the response lists what is missing. Both are normal responses.
     */
    @Transactional
    public InsuranceClaimResponse markReady(UUID accountId, AccountRole role, UUID claimId) {
        requireShipper(role);
        InsuranceClaim claim = ownedClaimForUpdate(claimId, accountId);
        if (!PREPARING.contains(claim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ này không còn ở giai đoạn chuẩn bị");
        }
        List<String> missing = missingItemsFor(claim);
        claim.setStatus(missing.isEmpty() ? InsuranceClaimStatus.READY_TO_SHARE : InsuranceClaimStatus.NEEDS_EVIDENCE);
        claim.setUpdatedAt(clock.instant());
        return view(claims.saveAndFlush(claim));
    }

    /**
     * The shipper hands the ready claim to the trip's carrier. It records the revision, the time and the shipper, and
     * the carrier can see that revision from then on. It is not a submission to any provider.
     */
    @Transactional
    public InsuranceClaimResponse share(UUID accountId, AccountRole role, UUID claimId) {
        requireShipper(role);
        InsuranceClaim claim = ownedClaimForUpdate(claimId, accountId);
        if (claim.getStatus() == InsuranceClaimStatus.SHARED_WITH_CARRIER
                || claim.getStatus() == InsuranceClaimStatus.CARRIER_ACKNOWLEDGED) {
            return view(claim);
        }
        if (claim.getStatus() != InsuranceClaimStatus.READY_TO_SHARE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ chưa đủ chứng cứ để chia sẻ với nhà xe");
        }
        if (!missingItemsFor(claim).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ còn thiếu chứng cứ, chưa chia sẻ được");
        }
        Instant now = clock.instant();
        claim.setSharedRevision(claim.getRevision());
        claim.setSharedAt(now);
        claim.setSharedBy(accountId);
        claim.setStatus(InsuranceClaimStatus.SHARED_WITH_CARRIER);
        claim.setUpdatedAt(now);
        return view(claims.saveAndFlush(claim));
    }

    /**
     * The carrier confirms it received the shared revision. Receipt only: it changes nothing the shipper submitted and
     * says nothing about any insurer's decision. Visible only to the trip's carrier after a share; anyone else gets 404.
     */
    @Transactional
    public CarrierClaimResponse acknowledge(UUID accountId, AccountRole role, UUID claimId) {
        requireCarrier(role);
        InsuranceClaim claim = sharedClaimForCarrierForUpdate(claimId, accountId);
        if (Objects.equals(claim.getCarrierAcknowledgedRevision(), claim.getSharedRevision())) {
            return carrierView(claim);
        }
        Instant now = clock.instant();
        claim.setCarrierAcknowledgedRevision(claim.getSharedRevision());
        claim.setCarrierAcknowledgedAt(now);
        claim.setCarrierAcknowledgedBy(accountId);
        if (claim.getStatus() == InsuranceClaimStatus.SHARED_WITH_CARRIER) {
            claim.setStatus(InsuranceClaimStatus.CARRIER_ACKNOWLEDGED);
        }
        claim.setUpdatedAt(now);
        return carrierView(claims.saveAndFlush(claim));
    }

    /** Claims of one trip, newest first. Visible to the trip's shipper and to admins only. */
    @Transactional(readOnly = true)
    public List<InsuranceClaimResponse> listForTrip(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        boolean allowed = role == AccountRole.ADMIN || (role == AccountRole.SHIPPER && accountId.equals(trip.getShipperId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Claim drafts are visible to the trip's shipper only");
        }
        return claims.findByTripIdOrderByCreatedAtDesc(tripId).stream().map(this::view).toList();
    }

    /** The claims the carrier has been shared, newest share first. Drafts never appear here. */
    @Transactional(readOnly = true)
    public List<CarrierClaimResponse> sharedWithCarrier(UUID accountId, AccountRole role) {
        requireCarrier(role);
        return claims.findByCarrierIdAndSharedRevisionIsNotNullOrderBySharedAtDesc(accountId).stream()
                .map(this::carrierView)
                .toList();
    }

    /** One evidence file, fetched from private storage after the caller is checked as the shipper or an admin. */
    @Transactional(readOnly = true)
    public EvidenceFile evidenceFile(UUID accountId, AccountRole role, UUID claimId, UUID evidenceId) {
        if (role != AccountRole.ADMIN && role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Claim evidence is visible to the shipper and admins only");
        }
        if (role == AccountRole.SHIPPER) {
            ownedClaim(claimId, accountId);
        } else if (!claims.existsById(claimId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }
        InsuranceClaimEvidence file = evidence.findByIdAndClaimId(evidenceId, claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
        Matcher key = EVIDENCE_KEY.matcher(file.getObjectKey());
        if (!key.matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found");
        }
        return media.fetch(key.group(1))
                .map(fetched -> new EvidenceFile(fetched.bytes(), fetched.contentType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Chưa tải được tệp chứng cứ. Vui lòng thử lại sau."));
    }

    /**
     * The claim pack as a ZIP. The shipper and admins get the current claim; the trip's carrier gets the shared revision
     * only, and only after a share. Any other account gets 404, so claim IDs do not reveal what exists.
     */
    @Transactional(readOnly = true)
    public ClaimPackageService.Packaged packageFor(UUID accountId, AccountRole role, UUID claimId) {
        InsuranceClaim claim = claims.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        ClaimPackageService.Audience audience;
        if (role == AccountRole.ADMIN || (role == AccountRole.SHIPPER && accountId.equals(claim.getShipperId()))) {
            audience = ClaimPackageService.Audience.SHIPPER_OR_ADMIN;
        } else if (role == AccountRole.CARRIER && accountId.equals(claim.getCarrierId()) && claim.getSharedRevision() != null) {
            audience = ClaimPackageService.Audience.CARRIER;
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }
        Trip trip = trips.findById(claim.getTripId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        return packages.build(claim, trip, audience);
    }

    private InsuranceClaim ownedClaim(UUID claimId, UUID accountId) {
        // Another shipper's claim reads as not found, so claim IDs do not reveal what exists.
        return claims.findByIdAndShipperId(claimId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    private InsuranceClaim ownedClaimForUpdate(UUID claimId, UUID accountId) {
        return claims.findByIdForUpdate(claimId)
                .filter(claim -> accountId.equals(claim.getShipperId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    /** A claim the carrier may act on: the trip's carrier, after the claim was shared. Otherwise 404. */
    private InsuranceClaim sharedClaimForCarrierForUpdate(UUID claimId, UUID accountId) {
        return claims.findByIdForUpdate(claimId)
                .filter(claim -> accountId.equals(claim.getCarrierId()) && claim.getSharedRevision() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    private List<String> missingItemsFor(InsuranceClaim claim) {
        List<InsuranceClaimEvidence> files = evidence.findByClaimIdOrderByUploadedAtAsc(claim.getId());
        return missingItems(claim, files, cargoDocuments.documentsOf(claim.getTripId()));
    }

    private static List<String> missingItems(InsuranceClaim claim, List<InsuranceClaimEvidence> files,
                                             List<TripCargoDocument> cargo) {
        return ClaimRequirements.missingItems(claim.getIncidentType(), claim.getClaimedAmount(), kindsOf(files),
                cargo.stream().map(TripCargoDocument::getKind)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(CargoDocumentKind.class))));
    }

    /** The shipper's and admin's view: every source of documents, with proxy links, and what is still missing. */
    private InsuranceClaimResponse view(InsuranceClaim claim) {
        List<InsuranceClaimEvidence> files = evidence.findByClaimIdOrderByUploadedAtAsc(claim.getId());
        List<TripCargoDocument> cargo = cargoDocuments.documentsOf(claim.getTripId());
        BigDecimal declared = trips.findById(claim.getTripId()).map(Trip::getDeclaredGoodsValue).orElse(null);
        HandoverContextResponse handover = handovers.handoverOf(claim.getTripId())
                .map(record -> HandoverContextResponse.of(record, claim.getTripId(), true))
                .orElse(null);
        return InsuranceClaimResponse.from(claim, files, missingItems(claim, files, cargo), declared,
                cargo.stream().map(CargoDocumentResponse::from).toList(), handover);
    }

    /** The carrier's view of a shared claim: the shared revision only, with metadata and no storage detail. */
    private CarrierClaimResponse carrierView(InsuranceClaim claim) {
        Instant sharedAt = claim.getSharedAt();
        int sharedRevision = claim.getSharedRevision();
        List<InsuranceClaimEvidence> files = evidence.findByClaimIdOrderByUploadedAtAsc(claim.getId()).stream()
                .filter(file -> file.getRevision() <= sharedRevision)
                .toList();
        List<TripCargoDocument> cargo = cargoDocuments.documentsOf(claim.getTripId()).stream()
                .filter(row -> !row.getUploadedAt().isAfter(sharedAt))
                .toList();
        HandoverContextResponse handover = handovers.handoverOf(claim.getTripId())
                .map(record -> HandoverContextResponse.of(record, claim.getTripId(), false))
                .orElse(null);
        return CarrierClaimResponse.from(claim, cargo, files, handover);
    }

    private static Set<ClaimEvidenceKind> kindsOf(List<InsuranceClaimEvidence> files) {
        return files.stream().map(InsuranceClaimEvidence::getKind)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ClaimEvidenceKind.class)));
    }

    private static void requireShipper(AccountRole role) {
        if (role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ chủ hàng của chuyến mới được chuẩn bị hồ sơ bồi thường");
        }
    }

    private static void requireCarrier(AccountRole role) {
        if (role != AccountRole.CARRIER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ nhà xe của chuyến mới xem được hồ sơ được chia sẻ");
        }
    }
}
