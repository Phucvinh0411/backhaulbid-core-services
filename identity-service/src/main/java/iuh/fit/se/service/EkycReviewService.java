package iuh.fit.se.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.domain.entity.*;
import iuh.fit.se.domain.enums.VerificationStatus;
import iuh.fit.se.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class EkycReviewService {
    private final EkycReviewReportRepository reports;
    private final EkycVerificationRepository verifications;
    private final EkycEvidenceCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;
    private final AccountRepository accounts;
    private final EkycReviewAuditRepository audit;
    private final NativeDecisionPolicy automatic;
    public record Check(String status, String source, String reasonCode) {}
    public record Overview(UUID id, UUID accountId, String status, String verificationMethod,
            NfcIdentitySummary identity, Map<String, Check> checks, Instant submittedAt, Instant reviewedAt,
            Instant evidenceExpiresAt, boolean evidenceAvailable, String reviewReason, long version,
            String captureConclusion, Map<String,String> sdkChecks) {}
    /**
     * One field of the owner's summary. {@code source} names where the value came from; a field without a
     * supported source is {@code available=false} with a null value, never a guess.
     */
    public record OwnerField(String value, String source, boolean available, boolean masked) {
        /** Creates a field marker when this flow has no verified source for the value. */
        static OwnerField missing() { return new OwnerField(null, "NOT_CAPTURED", false, false); }
    }
    /** What the owner sees: status, one short reason, and five identity fields. No checks or evidence lists. */
    public record OwnerSummary(UUID id, String status, String statusReason, Instant submittedAt, Instant reviewedAt,
            OwnerField fullName, OwnerField citizenId, OwnerField dateOfBirth, OwnerField gender,
            OwnerField residenceAddress, long version) {}
    public record QueueItem(UUID id, UUID accountId, String status, Instant submittedAt,
            Instant evidenceExpiresAt, boolean evidenceAvailable, long version) {}

    /** Reports whether encrypted NFC evidence can be stored with the current key. */
    public boolean available() { return cipher.available(); }

    /** Returns the explicitly configured local automatic-decision mode. */
    public String automaticMode() { return automatic.mode(); }

    /** Returns the latest status for an owner-owned session. */
    @Transactional(readOnly=true) public String sessionStatus(UUID owner, UUID id) {
        return reports.findById(id).filter(r -> owner.equals(r.getAccountId())).map(EkycReviewReport::getStatus).orElse("PENDING");
    }
    /** Returns the capture conclusion attached to an owner-owned session. */
    @Transactional(readOnly=true) public String sessionConclusion(UUID owner, UUID id) {
        return reports.findById(id).filter(r -> owner.equals(r.getAccountId())).map(EkycReviewReport::getCaptureConclusion).orElse("INCOMPLETE");
    }
    /** Returns the verification method attached to an owner-owned session. */
    @Transactional(readOnly=true) public String sessionMethod(UUID owner, UUID id) {
        return reports.findById(id).filter(r -> owner.equals(r.getAccountId())).map(EkycReviewReport::getVerificationMethod).orElse("NONE");
    }

    /** Caller holds the account lock and upload/session authorization; store once in the same transaction. */
    /** Stores encrypted NFC evidence and records the server-side review state. */
    @Transactional public void store(UUID owner, UUID session, VerifiedNativeEvidence evidence, NativeCaptureObservation observation) {
        if (!available()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Verification evidence storage unavailable");
        reports.supersedePending(owner, clock.instant());
        var row = new EkycReviewReport();
        row.setId(session); row.setAccountId(owner); row.setStatus("PENDING");
        row.setSubmittedAt(clock.instant()); row.setEvidenceExpiresAt(clock.instant().plus(Duration.ofDays(7)));
        row.setVerificationMethod("NONE");
        row.setCaptureConclusion(observation.conclusion());
        try { row.setSdkChecks(json.writeValueAsString(observation.checks())); }
        catch (Exception invalid) { throw error(HttpStatus.BAD_REQUEST,"Invalid capture checks"); }
        row.setSummaryEncrypted(encode(row, evidence.summary()));
        row.setFrontEncrypted(cipher.encrypt(evidence.photos().get("front"), session + ":front"));
        row.setBackEncrypted(cipher.encrypt(evidence.photos().get("back"), session + ":back"));
        row.setSelfieEncrypted(cipher.encrypt(evidence.photos().get("selfie"), session + ":selfie"));
        if (automatic.accepts(observation)) {
            var verification=verifications.findByAccount_Id(owner).orElseThrow(this::notFound);
            verification.setStatus(VerificationStatus.VERIFIED);
            verification.setFullName(evidence.summary().fullName());
            verification.setVerificationMethod("LOCAL_NFC_DEVELOPMENT");
            verification.setFailureReason(null);
            // Keep issuer/PAD fields unset: this is local business acceptance, not issuer assurance.
            verifications.save(verification);
            row.setStatus("VERIFIED"); row.setVerificationMethod("LOCAL_NFC_DEVELOPMENT");
            row.setReviewedAt(clock.instant()); row.setEvidenceExpiresAt(clock.instant().plus(Duration.ofHours(24)));
            row.setReviewReason("Tự động chấp nhận nghiệp vụ local theo chính sách " + automatic.mode());
        }
        reports.save(row);
        audit(owner, session, "VERIFIED".equals(row.getStatus()) ? "AUTO_LOCAL_ACCEPTED" : "SUBMITTED");
    }

    /** Returns the small identity summary shown to the submitting account. */
    @Transactional public OwnerSummary ownerOverview(UUID owner, boolean reveal) {
        var row=reports.findFirstByAccountIdOrderBySubmittedAtDesc(owner).orElseThrow(this::notFound);
        audit(owner, row.getId(), reveal ? "OWNER_REVEAL" : "OWNER_OVERVIEW");
        return ownerSummary(row, decode(row), reveal);
    }

    /**
     * Field sources (see docs/v3.1 eKYC table): name, date of birth and sex come from the TD1 MRZ inside DG1
     * whose hash matched EF.SOD; the 9-character MRZ document number is NOT the 12-digit citizen ID, and
     * no supported source carries the citizen ID or residence yet (DG13 layout unverified), so both stay
     * unavailable. Reports stored before this change decode the same way.
     */
    static OwnerSummary ownerSummary(EkycReviewReport row, NfcIdentitySummary identity, boolean reveal) {
        String status = row.getStatus();
        String reason = switch (status) {
            case "PENDING" -> "Hồ sơ đang chờ quản trị viên xem xét.";
            case "REJECTED" -> shortReason(row.getReviewReason());
            case "SUPERSEDED" -> "Đã có lần xác thực mới hơn.";
            default -> null;
        };
        boolean manualName = identity != null && identity.source() != null && identity.source().startsWith("MANUAL_REVIEW");
        String name = identity == null ? null : blankToNull(identity.fullName());
        OwnerField fullName = name == null ? OwnerField.missing()
                : new OwnerField(name, manualName ? "MANUAL_REVIEW" : "CHIP_DG1_MRZ", true, false);
        String birth = identity == null ? null : blankToNull(identity.dateOfBirth());
        // MRZ stores a 2-digit year; the century follows the parser policy "born within the last 100 years".
        OwnerField dateOfBirth = birth == null ? OwnerField.missing()
                : new OwnerField(reveal ? birth : null, "CHIP_DG1_MRZ_CENTURY_POLICY", true, !reveal);
        String sex = identity == null ? null : blankToNull(identity.sex());
        OwnerField gender = sex == null || !Set.of("M", "F").contains(sex) ? OwnerField.missing()
                : new OwnerField(sex, "CHIP_DG1_MRZ", true, false);
        return new OwnerSummary(row.getId(), status, reason, row.getSubmittedAt(), row.getReviewedAt(),
                fullName, OwnerField.missing(), dateOfBirth, gender, OwnerField.missing(), row.getVersion());
    }
    /** Trims review text to the length allowed in the owner's status summary. */
    private static String shortReason(String reason) {
        if (reason == null || reason.isBlank()) return "Hồ sơ chưa được chấp nhận.";
        String trimmed = reason.trim();
        return trimmed.length() > 160 ? trimmed.substring(0, 157) + "..." : trimmed;
    }
    /** Normalizes empty identity fields to null before presenting them. */
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    /** Returns the admin review detail and records access in the audit trail. */
    @Transactional public Overview adminOverview(UUID admin, UUID id) {
        audit(admin, id, "ADMIN_OVERVIEW");
        return overview(reports.findById(id).orElseThrow(this::notFound), true);
    }
    /** Lists paginated reports for an allowed admin review status. */
    @Transactional public Page<QueueItem> queue(UUID admin, String status, int page, int size) {
        if (!Set.of("PENDING", "VERIFIED", "REJECTED", "SUPERSEDED").contains(status) || page<0 || size<1 || size>100)
            throw error(HttpStatus.BAD_REQUEST, "Invalid verification filter");
        audit(admin, null, "ADMIN_QUEUE");
        return reports.findByStatusOrderBySubmittedAtAsc(status, PageRequest.of(page, size)).map(r ->
                new QueueItem(r.getId(), r.getAccountId(), r.getStatus(), r.getSubmittedAt(),
                        r.getEvidenceExpiresAt(), evidenceAvailable(r), r.getVersion()));
    }
    /** Loads evidence only for the account that submitted the report. */
    @Transactional public byte[] ownerEvidence(UUID owner, UUID id, String part) {
        var row = reports.findById(id).orElseThrow(this::notFound);
        if (!row.getAccountId().equals(owner)) throw notFound();
        byte[] bytes=image(row, part); audit(owner, id, "OWNER_EVIDENCE"); return bytes;
    }
    /** Loads admin-authorized evidence and records the access event. */
    @Transactional public byte[] adminEvidence(UUID admin, UUID id, String part) {
        byte[] bytes=image(reports.findById(id).orElseThrow(this::notFound), part);
        audit(admin, id, "ADMIN_EVIDENCE"); return bytes;
    }

    /** Approves or rejects a version-matched report after required manual comparisons. */
    @Transactional public Overview review(UUID admin, UUID id, long version, String decision, String reason,
            boolean documentCompared, boolean faceCompared, String reviewedName) {
        var initial = reports.findById(id).orElseThrow(this::notFound);
        // Same account->report lock order as capture, preventing review/recapture races.
        accounts.lockForCapture(initial.getAccountId()).orElseThrow(this::notFound);
        var row = reports.lockById(id).orElseThrow(this::notFound);
        if (!"PENDING".equals(row.getStatus()) || row.getVersion()!=version)
            throw error(HttpStatus.CONFLICT, "Verification was already changed; reload the report");
        if (!Set.of("APPROVE", "REJECT").contains(decision) || reason==null || reason.isBlank() || reason.length()>500)
            throw error(HttpStatus.BAD_REQUEST, "A valid decision and review reason are required");
        boolean approve = "APPROVE".equals(decision);
        if (approve && (!documentCompared || !faceCompared || !evidenceAvailable(row)))
            throw error(HttpStatus.CONFLICT, "Review the document and face using available evidence before approval");
        NfcIdentitySummary identity = decode(row);
        String name = reviewedName==null || reviewedName.isBlank() ? identity.fullName() : reviewedName.trim();
        if (approve && (name.isBlank() || name.length()>255 || name.chars().anyMatch(Character::isISOControl)))
            throw error(HttpStatus.BAD_REQUEST, "A valid representative name is required");
        if (approve && !name.equals(identity.fullName())) {
            row.setSummaryEncrypted(encode(row, new NfcIdentitySummary(name, identity.documentNumber(),
                    identity.dateOfBirth(), identity.dateOfExpiry(), identity.sex(), identity.nationality(),
                    identity.issuingCountry(), "MANUAL_REVIEW_NAME_WITH_SIGNED_DG1_FIELDS")));
        }
        var verification = verifications.findByAccount_Id(row.getAccountId()).orElseThrow(this::notFound);
        if (verification.getStatus()==VerificationStatus.VERIFIED)
            throw error(HttpStatus.CONFLICT, "Representative is already verified");
        row.setStatus(approve ? "VERIFIED" : "REJECTED"); row.setReviewedBy(admin); row.setReviewedAt(clock.instant());
        row.setReviewReason(reason.trim()); row.setDocumentCompared(approve && documentCompared);
        row.setFaceCompared(approve && faceCompared); row.setVerificationMethod("MANUAL_DOCUMENT_REVIEW");
        row.setEvidenceExpiresAt(clock.instant().plus(Duration.ofHours(24)));
        verification.setStatus(approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
        verification.setFullName(approve ? name : null);
        verification.setVerificationMethod("MANUAL_DOCUMENT_REVIEW");
        verification.setFailureReason(approve ? null : reason.trim());
        // No assertion of issuer trust, automated face matching, or PAD from manual review.
        verifications.save(verification); reports.saveAndFlush(row);
        audit(admin, id, approve ? "APPROVED" : "REJECTED");
        return overview(row, true);
    }
    /** Deletes encrypted evidence whose retention deadline has passed. */
    @Transactional public int purgeExpiredEvidence() { return reports.purgeExpiredEvidence(clock.instant()); }

    /** Builds the detailed report view and masks sensitive identity fields when requested. */
    private Overview overview(EkycReviewReport row, boolean reveal) {
        var identity = decode(row);
        if (!reveal) identity = new NfcIdentitySummary(identity.fullName(), "•••••" + identity.documentNumber().substring(Math.max(0, identity.documentNumber().length()-4)),
                "••/••/••••", identity.dateOfExpiry(), identity.sex(), identity.nationality(), identity.issuingCountry(), identity.source());
        Map<String, Check> checks = new LinkedHashMap<>();
        checks.put("chipIntegrity", new Check("PASS", "SERVER_SOD_PRESENTED_DS", "DG1_DG2_SIGNATURE_AND_HASH_VALID"));
        checks.put("issuerTrust", new Check("UNAVAILABLE", "SERVER", "ISSUER_TRUST_STORE_NOT_CONFIGURED"));
        checks.put("documentComparison", new Check(row.isDocumentCompared() ? "PASS" : "NOT_EVALUATED", "MANUAL_REVIEW", "DOCUMENT_REVIEW"));
        checks.put("faceComparison", new Check(row.isFaceCompared() ? "PASS" : "NOT_EVALUATED", "MANUAL_REVIEW", "FACE_REVIEW"));
        checks.put("liveness", new Check("UNAVAILABLE", "SERVER", "SERVER_LIVENESS_PROVIDER_NOT_CONFIGURED"));
        return new Overview(row.getId(), row.getAccountId(), row.getStatus(), row.getVerificationMethod(), identity,
                checks, row.getSubmittedAt(), row.getReviewedAt(), row.getEvidenceExpiresAt(), evidenceAvailable(row), row.getReviewReason(), row.getVersion(),
                row.getCaptureConclusion(), sdkChecks(row));
    }
    /** Deserializes the validated device-check summary retained with the report. */
    private Map<String,String> sdkChecks(EkycReviewReport row) {
        try { return json.readValue(row.getSdkChecks(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {}); }
        catch (Exception invalid) { throw error(HttpStatus.SERVICE_UNAVAILABLE,"Capture report unavailable"); }
    }
    /** Checks report state, expiry and presence of every encrypted evidence image. */
    private boolean evidenceAvailable(EkycReviewReport row) {
        return !"SUPERSEDED".equals(row.getStatus()) && clock.instant().isBefore(row.getEvidenceExpiresAt()) &&
                row.getFrontEncrypted()!=null && row.getBackEncrypted()!=null && row.getSelfieEncrypted()!=null;
    }
    /** Decrypts one approved evidence image using report and part as cipher context. */
    private byte[] image(EkycReviewReport row, String part) {
        if (!evidenceAvailable(row)) throw error(HttpStatus.GONE, "Verification evidence has expired");
        byte[] value = switch(part) { case "front" -> row.getFrontEncrypted(); case "back" -> row.getBackEncrypted();
            case "selfie" -> row.getSelfieEncrypted(); default -> throw notFound(); };
        return cipher.decrypt(value, row.getId()+":"+part);
    }
    /** Serializes and encrypts the identity summary, clearing the temporary plaintext. */
    private byte[] encode(EkycReviewReport row, NfcIdentitySummary summary) {
        byte[] plain = null;
        try { plain=json.writeValueAsBytes(summary); return cipher.encrypt(plain, row.getId()+":summary"); }
        catch (ResponseStatusException safe) { throw safe; }
        catch (Exception failure) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "Verification summary unavailable"); }
        finally { if (plain!=null) Arrays.fill(plain, (byte)0); }
    }
    /** Decrypts and parses the identity summary, then clears its temporary plaintext. */
    private NfcIdentitySummary decode(EkycReviewReport row) {
        byte[] plain = cipher.decrypt(row.getSummaryEncrypted(), row.getId()+":summary");
        try { return json.readValue(plain, NfcIdentitySummary.class); }
        catch (Exception failure) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "Verification summary unavailable"); }
        finally { Arrays.fill(plain, (byte)0); }
    }
    /** Creates the non-disclosing not-found response used for reports and evidence. */
    private ResponseStatusException notFound() { return error(HttpStatus.NOT_FOUND, "Verification report not found"); }

    /** Persists a metadata-only audit event for report access or review actions. */
    private void audit(UUID actor, UUID report, String action) {
        var entry=new EkycReviewAudit(); entry.setActorId(actor); entry.setReportId(report);
        entry.setAction(action); entry.setOccurredAt(clock.instant()); audit.save(entry);
    }
    /** Creates a consistent HTTP error for invalid or unavailable report operations. */
    private static ResponseStatusException error(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
}
