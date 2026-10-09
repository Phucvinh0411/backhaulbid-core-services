package iuh.fit.se.controller;

import iuh.fit.se.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.Semaphore;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/representative-verifications/sessions")
@PreAuthorize("hasAnyRole('SHIPPER', 'CARRIER')")
public class EkycCaptureSessionController {
    private final EkycCaptureSessionService sessions;
    private final NativeEvidenceIntegrityVerifier verifier;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final Semaphore uploads = new Semaphore(2);
    public record Pair(@NotBlank @Size(max=128) String pairingToken, @Pattern(regexp="[A-Za-z0-9_-]{32,128}") @NotNull String deviceNonce) {}
    public record Approve(@Pattern(regexp="[0-9]{6}") @NotNull String confirmationCode) {}
    public record Device(@Pattern(regexp="[A-Za-z0-9_-]{32,128}") @NotNull String deviceNonce) {}
    /** Resolves the authenticated account ID used to authorize session access. */
    private UUID owner(Authentication auth) { return UUID.fromString(auth.getName()); }

    /** Reports whether QR/NFC capture is enabled and describes the active policy. */
    @GetMapping("/capabilities") public Map<String, Object> capabilities() { return sessions.capabilities(); }

    /** Creates a QR pairing session for the authenticated business account. */
    @PostMapping public EkycCaptureSessionService.Created create(Authentication auth) { return sessions.create(owner(auth)); }

    /** Returns the current state of an account-owned capture session. */
    @GetMapping("/{id}") public EkycCaptureSessionService.Status status(Authentication auth, @PathVariable UUID id) { return sessions.status(owner(auth), id); }

    /** Pairs the mobile device after it presents the QR token and device nonce. */
    @PostMapping("/{id}/pair") public EkycCaptureSessionService.Status pair(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Pair body) {
        return sessions.pair(owner(auth), id, body.pairingToken(), body.deviceNonce());
    }

    /** Confirms the mobile pairing code entered by the web account owner. */
    @PostMapping("/{id}/approve") public EkycCaptureSessionService.Status approve(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Approve body) {
        return sessions.approve(owner(auth), id, body.confirmationCode());
    }

    /** Issues the scoped upload grant after the owner approves the paired phone. */
    @PostMapping("/{id}/ready") public EkycCaptureSessionService.Ready ready(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Device body) {
        return sessions.ready(owner(auth), id, body.deviceNonce());
    }

    /** Cancels an unfinished account-owned capture session. */
    @PostMapping("/{id}/cancel") public EkycCaptureSessionService.Status cancel(Authentication auth, @PathVariable UUID id) { return sessions.cancel(owner(auth), id); }

    /** Returns the idempotent receipt for a previously accepted mobile capture. */
    @GetMapping("/{id}/receipt") public EkycCaptureSessionService.Receipt receipt(Authentication auth, @PathVariable UUID id,
            @RequestHeader("X-Device-Nonce") String device, @RequestHeader("X-Upload-Grant") String grant,
            @RequestHeader("X-Attempt-Id") UUID attempt) {
        return sessions.acknowledged(owner(auth), id, device, grant, attempt);
    }

    /** Validates and stores encrypted capture evidence for the paired NFC session. */
    @PostMapping(value="/{id}/evidence", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public EkycCaptureSessionService.Receipt evidence(Authentication auth, @PathVariable UUID id,
            @RequestHeader("X-Device-Nonce") String device, @RequestHeader("X-Upload-Grant") String grant,
            @RequestHeader("X-Attempt-Id") UUID attempt, @RequestHeader(value="X-Evidence-Consent", required=false) String consent,
            @RequestHeader(value="X-Capture-Report", required=false) String captureReport,
            @RequestParam MultiValueMap<String, MultipartFile> files) {
        if (!EkycCaptureSessionService.POLICY.equals(consent))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Update the app and consent to verification evidence retention");
        if (!uploads.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence capacity reached; retry later");
        try {
            sessions.validateUpload(owner(auth), id, device, grant);
            var observation=NativeCaptureObservation.parse(captureReport,id,attempt,json);
            if (!observation.capturedChecksComplete()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Capture checks are incomplete");
            Map<String, MultipartFile> parts = new HashMap<>();
            files.forEach((name, values) -> {
                if (values.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate evidence part");
                parts.put(name, values.getFirst());
            });
            try (var evidence=verifier.inspect(parts)) {
                return sessions.accept(owner(auth), id, device, grant, attempt, evidence, observation);
            }
        } finally { uploads.release(); }
    }
}
