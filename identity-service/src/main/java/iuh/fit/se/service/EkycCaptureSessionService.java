package iuh.fit.se.service;

import iuh.fit.se.domain.entity.*;
import iuh.fit.se.domain.enums.*;
import iuh.fit.se.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static iuh.fit.se.domain.entity.EkycCaptureSession.State.*;

@Service
public class EkycCaptureSessionService {
    public static final String POLICY = "nfc-review-v2";
    public static final String PENDING_REASON = "Đã tiếp nhận kết quả NFC. Kết luận và mức kiểm chứng từng bước được ghi trong hồ sơ.";
    private static final List<EkycCaptureSession.State> ACTIVE = List.of(CREATED, PAIRED, APPROVED, CAPTURING);
    private final AccountRepository accounts;
    private final EkycCaptureSessionRepository sessions;
    private final EkycVerificationRepository verifications;
    private final Clock clock;
    private final boolean enabled;
    private final EkycReviewService reviews;
    private final SecureRandom random = new SecureRandom();

    /** Wires repositories, policy settings and evidence review services for NFC sessions. */
    public EkycCaptureSessionService(AccountRepository accounts, EkycCaptureSessionRepository sessions,
            EkycVerificationRepository verifications, Clock clock, @Value("${app.ekyc.nfc-enabled:false}") boolean enabled,
            EkycReviewService reviews) {
        this.accounts = accounts; this.sessions = sessions; this.verifications = verifications;
        this.clock = clock; this.enabled = enabled;
        this.reviews = reviews;
    }
    public record Created(UUID sessionId, String pairingToken, String qrPayload, Instant expiresAt, String state) {}
    public record Status(UUID sessionId, String state, String confirmationCode, Instant expiresAt, String verificationStatus, String captureConclusion) {}
    public record Ready(UUID sessionId, UUID accountId, String role, Instant expiresAt, String policyVersion, String uploadGrant) {}
    public record Receipt(UUID sessionId, UUID attemptId, UUID receiptId, String status, String conclusion, String verificationMethod) {}
    /** Publishes the active session policy and evidence-retention limits to the portal. */
    public Map<String, Object> capabilities() { return Map.of("enabled", enabled, "productionReady", false,
            "manualReviewAvailable", reviews.available(), "policyVersion", POLICY, "evidenceRetentionDays", 7,
            "reviewedEvidenceRetentionHours", 24, "automaticMode", Optional.ofNullable(reviews.automaticMode()).orElse("DISABLED")); }

    /** Creates a short-lived pairing session and retires the owner's previous active sessions. */
    @Transactional public Created create(UUID owner) {
        requireEnabled();
        Account account = authorizedAccount(owner);
        if (verifications.findByAccount_Id(owner).filter(v -> v.getStatus() == VerificationStatus.VERIFIED).isPresent())
            throw error(HttpStatus.CONFLICT, "Identity is already verified");
        if (sessions.countByAccountIdAndCreatedAtAfter(owner, clock.instant().minusSeconds(600)) >= 5)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Too many verification sessions");
        for (var old : sessions.findByAccountIdAndStateIn(owner, ACTIVE)) {
            old.setState(CANCELLED); clearSecrets(old);
        }
        sessions.flush(); // Free the partial unique index before inserting the next session.
        String token = token();
        var row = new EkycCaptureSession();
        row.setAccountId(owner); row.setRole(account.getRole()); row.setState(CREATED);
        row.setCreatedAt(clock.instant()); row.setExpiresAt(clock.instant().plusSeconds(600));
        row.setPairingTokenHash(hash(token));
        row = sessions.save(row);
        String qr = "{\"v\":1,\"sessionId\":\"" + row.getId() + "\",\"pairingToken\":\"" + token + "\"}";
        return new Created(row.getId(), token, qr, row.getExpiresAt(), CREATED.name());
    }

    /** Pairs an authenticated mobile device using the QR token and a fresh device nonce. */
    @Transactional public Status pair(UUID owner, UUID id, String token, String device) {
        requireEnabled();
        var row = owned(owner, id); requireLive(row); requireState(row, CREATED);
        validateNonce(device);
        if (!matches(row.getPairingTokenHash(), token)) throw error(HttpStatus.FORBIDDEN, "Invalid pairing token");
        row.setPairingTokenHash(null); row.setDeviceNonceHash(hash(device));
        // Never 000000, so placeholder confirmation cannot authorize capture.
        row.setConfirmationCode(String.format(Locale.ROOT, "%06d", 100000 + random.nextInt(900000)));
        row.setState(PAIRED);
        return view(row);
    }

    /** Confirms on the web that the mobile device belongs to the signed-in user. */
    @Transactional public Status approve(UUID owner, UUID id, String confirmation) {
        requireEnabled();
        var row = owned(owner, id); requireLive(row); requireState(row, PAIRED);
        if (confirmation == null || !MessageDigest.isEqual(row.getConfirmationCode().getBytes(StandardCharsets.UTF_8), confirmation.getBytes(StandardCharsets.UTF_8)))
            throw error(HttpStatus.CONFLICT, "Confirmation code does not match");
        row.setState(APPROVED); return view(row);
    }

    /** Issues a scoped upload grant after the web user approves the paired device. */
    @Transactional public Ready ready(UUID owner, UUID id, String device) {
        requireEnabled();
        var row = owned(owner, id); requireLive(row);
        if (row.getState() != APPROVED && row.getState() != CAPTURING) throw error(HttpStatus.CONFLICT, "Approve the mobile device on web first");
        requireDevice(row, device);
        String grant = token(); row.setUploadGrantHash(hash(grant)); row.setState(CAPTURING);
        return new Ready(row.getId(), owner, row.getRole().name(), row.getExpiresAt(), POLICY, grant);
    }

    /** Returns session status and expires the session when its lifetime has elapsed. */
    @Transactional public Status status(UUID owner, UUID id) {
        var row = owned(owner, id);
        if (ACTIVE.contains(row.getState()) && !clock.instant().isBefore(row.getExpiresAt())) {
            row.setState(EXPIRED); clearSecrets(row);
        }
        return view(row);
    }

    /** Cancels an unfinished session and erases its temporary pairing credentials. */
    @Transactional public Status cancel(UUID owner, UUID id) {
        var row = owned(owner, id);
        if (row.getState() != SUBMITTED) { row.setState(CANCELLED); clearSecrets(row); }
        return view(row);
    }

    /** Called only after server checks every multipart artifact; claims cannot select the result. */
    @Transactional public Receipt accept(UUID owner, UUID id, String device, String grant, UUID attempt, VerifiedNativeEvidence evidence,
            NativeCaptureObservation observation) {
        if (!id.equals(observation.sessionId()) || !attempt.equals(observation.attemptId()) ||
                !POLICY.equals(observation.policyVersion()) || !observation.capturedChecksComplete() ||
                !observation.derivedConclusion().equals(observation.conclusion()))
            throw error(HttpStatus.BAD_REQUEST, "Invalid capture report binding");
        String digest=hash(evidence.digest() + ":" + observation.canonical());
        requireEnabled();
        Account account = authorizedAccount(owner);
        var row = owned(owner, id); requireDevice(row, device);
        if (!matches(row.getUploadGrantHash(), grant)) throw error(HttpStatus.FORBIDDEN, "Invalid upload grant");
        if (row.getState() == SUBMITTED) {
            if (!attempt.equals(row.getAttemptId()) || !digest.equals(row.getEvidenceDigest()))
                throw error(HttpStatus.CONFLICT, "Evidence differs from acknowledged submission");
            return receipt(row);
        }
        requireLive(row); requireState(row, CAPTURING);
        var verification = verifications.findByAccount_Id(owner).orElseGet(() -> EkycVerification.builder().account(account).build());
        if (verification.getStatus() == VerificationStatus.VERIFIED) throw error(HttpStatus.CONFLICT, "Identity is already verified");
        verification.setStatus(VerificationStatus.PENDING); verification.setFailureReason(PENDING_REASON);
        verification.setVerificationMethod("NONE");
        verification.setIdentityNumber(null); verification.setFullName(null);
        verification.setFrontImageUrl(null); verification.setBackImageUrl(null); verification.setSelfieImageUrl(null);
        verification.setLivenessVideoUrl(null); verification.setFaceMatchScore(null);
        verification.setOcrPassed(null); verification.setDocumentLivenessPassed(null);
        verification.setDocumentAuthenticityPassed(null); verification.setLivenessPassed(null); verification.setFaceMatched(null);
        verifications.save(verification);
        reviews.store(owner, id, evidence, observation);
        row.setAttemptId(attempt); row.setReceiptId(UUID.randomUUID()); row.setEvidenceDigest(digest); row.setState(SUBMITTED);
        return receipt(row);
    }

    /** Checks the paired-device proof and upload grant before accepting multipart evidence. */
    @Transactional public void validateUpload(UUID owner, UUID id, String device, String grant) {
        requireEnabled();
        var row = owned(owner, id); requireDevice(row, device);
        if (!matches(row.getUploadGrantHash(), grant)) throw error(HttpStatus.FORBIDDEN, "Invalid upload grant");
        if (row.getState() != SUBMITTED) { requireLive(row); requireState(row, CAPTURING); }
    }

    /** Returns the committed receipt for an idempotent retry of the same capture attempt. */
    @Transactional public Receipt acknowledged(UUID owner, UUID id, String device, String grant, UUID attempt) {
        requireEnabled();
        var row = owned(owner, id); requireDevice(row, device);
        if (!matches(row.getUploadGrantHash(), grant)) throw error(HttpStatus.FORBIDDEN, "Invalid upload grant");
        if (row.getState() != SUBMITTED) throw error(HttpStatus.NOT_FOUND, "Receipt not committed");
        if (!attempt.equals(row.getAttemptId())) throw error(HttpStatus.CONFLICT, "Different capture attempt");
        return receipt(row);
    }

    /** Locks the account and limits verification sessions to active shipper/carrier accounts. */
    private Account authorizedAccount(UUID owner) {
        Account account = accounts.lockForCapture(owner).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE || (account.getRole() != AccountRole.SHIPPER && account.getRole() != AccountRole.CARRIER))
            throw error(HttpStatus.FORBIDDEN, "Role cannot verify a representative");
        return account;
    }
    /** Loads a locked session only when it belongs to the requesting account. */
    private EkycCaptureSession owned(UUID owner, UUID id) {
        var row = sessions.lockById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Session not found"));
        if (!owner.equals(row.getAccountId())) throw error(HttpStatus.NOT_FOUND, "Session not found");
        return row;
    }
    /** Rejects session operations when NFC or encrypted evidence storage is unavailable. */
    private void requireEnabled() {
        if (!enabled) throw error(HttpStatus.SERVICE_UNAVAILABLE, "NFC verification is not enabled");
        if (!reviews.available()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Verification evidence storage unavailable");
    }
    /** Rejects operations on an expired session. */
    private void requireLive(EkycCaptureSession row) {
        if (!clock.instant().isBefore(row.getExpiresAt())) throw error(HttpStatus.GONE, "Session expired");
    }
    /** Enforces the expected session transition before mutating the record. */
    private void requireState(EkycCaptureSession row, EkycCaptureSession.State state) {
        if (row.getState() != state) throw error(HttpStatus.CONFLICT, "Invalid session state");
    }
    /** Validates the URL-safe device nonce format and accepted length. */
    private static void validateNonce(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{32,128}")) throw error(HttpStatus.BAD_REQUEST, "Invalid device nonce");
    }
    /** Verifies that a request came from the device paired to this session. */
    private void requireDevice(EkycCaptureSession row, String value) {
        validateNonce(value);
        if (!matches(row.getDeviceNonceHash(), value)) throw error(HttpStatus.FORBIDDEN, "Different paired device");
    }
    /** Removes temporary tokens and confirmation codes from a closed session. */
    private static void clearSecrets(EkycCaptureSession row) {
        row.setPairingTokenHash(null); row.setDeviceNonceHash(null); row.setUploadGrantHash(null); row.setConfirmationCode(null);
    }
    /** Maps a persistence row to the status payload visible to its owner. */
    private Status view(EkycCaptureSession row) {
        return new Status(row.getId(), row.getState().name(), row.getConfirmationCode(), row.getExpiresAt(),
                row.getState() == SUBMITTED ? reviews.sessionStatus(row.getAccountId(), row.getId()) : null,
                row.getState() == SUBMITTED ? reviews.sessionConclusion(row.getAccountId(), row.getId()) : null);
    }
    /** Maps a submitted session to the stable receipt returned to the mobile app. */
    private Receipt receipt(EkycCaptureSession row) {
        return new Receipt(row.getId(), row.getAttemptId(), row.getReceiptId(),
                Optional.ofNullable(reviews.sessionStatus(row.getAccountId(),row.getId())).orElse("PENDING"),
                reviews.sessionConclusion(row.getAccountId(),row.getId()), reviews.sessionMethod(row.getAccountId(),row.getId()));
    }
    /** Creates a cryptographically random URL-safe session secret. */
    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    /** Hashes a secret before storing or comparing it with persistent session state. */
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    /** Compares a supplied secret to its stored hash without leaking timing information. */
    private static boolean matches(String expected, String supplied) {
        return expected != null && supplied != null && supplied.length() <= 128
            && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), hash(supplied).getBytes(StandardCharsets.UTF_8));
    }
    /** Creates a consistent HTTP error for invalid session operations. */
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
