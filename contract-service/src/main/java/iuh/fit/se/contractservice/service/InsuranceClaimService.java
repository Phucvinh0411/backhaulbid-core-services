package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.InsuranceClaim;
import iuh.fit.se.contractservice.domain.entity.InsuranceClaimEvidence;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.AddClaimEvidenceRequest;
import iuh.fit.se.contractservice.dto.CreateInsuranceClaimRequest;
import iuh.fit.se.contractservice.dto.InsuranceClaimResponse;
import iuh.fit.se.contractservice.repository.InsuranceClaimEvidenceRepository;
import iuh.fit.se.contractservice.repository.InsuranceClaimRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.service.driveraccess.UuidV7;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Claim drafts for a trip's goods: the shipper opens a draft, attaches private evidence, and marks it ready once
 * the checklist is complete. Marking ready never sends anything to a provider; the provider's own states are
 * never set here. Evidence files are private: only the shipper and admins can fetch them, through this service.
 */
@Service
public class InsuranceClaimService {
    public static final int MAX_EVIDENCE = 20;
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    /** Goods are on the vehicle from pickup until the shipper accepted delivery. */
    private static final Set<TripStatus> CLAIMABLE = EnumSet.of(TripStatus.PICKED_UP, TripStatus.IN_TRANSIT,
            TripStatus.DELIVERED, TripStatus.COMPLETED);
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
    private final Clock clock;
    private final UuidV7 ids;

    public InsuranceClaimService(TripRepository trips, InsuranceClaimRepository claims, InsuranceClaimEvidenceRepository evidence,
                                 MediaEvidenceClient media, Clock clock) {
        this.trips = trips;
        this.claims = claims;
        this.evidence = evidence;
        this.media = media;
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
                .createdAt(now)
                .updatedAt(now)
                .build();
        return view(claims.saveAndFlush(claim));
    }

    /** Attaches one already-uploaded private file. Refused once the draft is marked ready. */
    @Transactional
    public InsuranceClaimResponse addEvidence(UUID accountId, AccountRole role, UUID claimId, AddClaimEvidenceRequest request) {
        requireShipper(role);
        InsuranceClaim claim = ownedClaim(claimId, accountId);
        if (claim.getStatus() != InsuranceClaimStatus.DRAFT && claim.getStatus() != InsuranceClaimStatus.NEEDS_EVIDENCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ đã sẵn sàng, không thêm được chứng cứ");
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
        if (evidence.countByClaimId(claimId) >= MAX_EVIDENCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ đã đủ số lượng chứng cứ tối đa");
        }
        Instant now = clock.instant();
        evidence.saveAndFlush(InsuranceClaimEvidence.builder()
                .id(ids.next())
                .claimId(claimId)
                .kind(request.kind())
                .objectKey(objectKey)
                .uploadedBy(accountId)
                .uploadedAt(now)
                .build());
        claim.setUpdatedAt(now);
        return view(claims.saveAndFlush(claim));
    }

    /**
     * Checks the checklist. Complete: the draft becomes READY_FOR_PROVIDER (still not sent). Incomplete: it becomes
     * NEEDS_EVIDENCE and the response lists what is missing. Both are normal responses, so the status is kept.
     */
    @Transactional
    public InsuranceClaimResponse markReady(UUID accountId, AccountRole role, UUID claimId) {
        requireShipper(role);
        InsuranceClaim claim = ownedClaim(claimId, accountId);
        if (claim.getStatus() != InsuranceClaimStatus.DRAFT
                && claim.getStatus() != InsuranceClaimStatus.NEEDS_EVIDENCE
                && claim.getStatus() != InsuranceClaimStatus.READY_FOR_PROVIDER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ này không còn ở giai đoạn chuẩn bị");
        }
        List<InsuranceClaimEvidence> files = evidence.findByClaimIdOrderByUploadedAtAsc(claimId);
        List<String> missing = ClaimRequirements.missingItems(claim.getIncidentType(), claim.getClaimedAmount(), kindsOf(files));
        claim.setStatus(missing.isEmpty() ? InsuranceClaimStatus.READY_FOR_PROVIDER : InsuranceClaimStatus.NEEDS_EVIDENCE);
        claim.setUpdatedAt(clock.instant());
        return InsuranceClaimResponse.from(claims.saveAndFlush(claim), files, missing);
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

    private InsuranceClaim ownedClaim(UUID claimId, UUID accountId) {
        // Another shipper's claim reads as not found, so claim IDs do not reveal what exists.
        return claims.findByIdAndShipperId(claimId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    private InsuranceClaimResponse view(InsuranceClaim claim) {
        List<InsuranceClaimEvidence> files = evidence.findByClaimIdOrderByUploadedAtAsc(claim.getId());
        List<String> missing = ClaimRequirements.missingItems(claim.getIncidentType(), claim.getClaimedAmount(), kindsOf(files));
        return InsuranceClaimResponse.from(claim, files, missing);
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
}
