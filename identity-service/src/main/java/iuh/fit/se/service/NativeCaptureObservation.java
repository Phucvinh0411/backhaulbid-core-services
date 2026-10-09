package iuh.fit.se.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** SDK observations preserve its conclusion; they are not trusted issuer/PAD evidence. */
public record NativeCaptureObservation(UUID sessionId, UUID attemptId, String policyVersion,
        String conclusion, Map<String,String> checks) {
    private static final Set<String> NAMES=Set.of("documentReadiness","nfcRead","documentComparison",
            "activeLiveness","passiveLiveness","faceMatch");
    private static final Set<String> VALUES=Set.of("PASS","DEVELOPMENT_PASS","FAIL","NOT_RUN","INCONCLUSIVE","ERROR","CANCELLED");

    /** Freezes the validated check map so callers cannot alter the capture report. */
    public NativeCaptureObservation { checks=Map.copyOf(checks); }

    /** Decodes a bounded report and binds it to the server session, attempt and policy. */
    public static NativeCaptureObservation parse(String encoded, UUID session, UUID attempt, ObjectMapper json) {
        byte[] bytes=null;
        try {
            if (encoded==null || encoded.length()>4096) throw new IllegalArgumentException();
            bytes=Base64.getUrlDecoder().decode(encoded);
            var result=json.readerFor(NativeCaptureObservation.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .<NativeCaptureObservation>readValue(bytes);
            if (!session.equals(result.sessionId()) || !attempt.equals(result.attemptId()) ||
                    !EkycCaptureSessionService.POLICY.equals(result.policyVersion()) || !result.checks().keySet().equals(NAMES) ||
                    !result.checks().values().stream().allMatch(VALUES::contains) || !result.derivedConclusion().equals(result.conclusion()))
                throw new IllegalArgumentException();
            return result;
        } catch (Exception invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid capture observation report"); }
        finally { if (bytes!=null) Arrays.fill(bytes,(byte)0); }
    }
    /** Derives the capture conclusion from the individual check outcomes. */
    public String derivedConclusion() {
        boolean ready="PASS".equals(checks.get("documentReadiness")), nfc="PASS".equals(checks.get("nfcRead"));
        boolean biometric=List.of("activeLiveness","passiveLiveness","faceMatch").stream().allMatch(k -> "PASS".equals(checks.get(k)));
        if (ready && nfc && biometric) return "COMPLETED";
        if ("FAIL".equals(checks.get("nfcRead"))) return "FAILED_STAGE";
        if (!ready || "NOT_RUN".equals(checks.get("nfcRead"))) return "INCOMPLETE";
        return "NEEDS_REVIEW";
    }
    /** Checks whether all required on-device observations were submitted. */
    public boolean capturedChecksComplete() {
        return List.of("documentReadiness","nfcRead","documentComparison").stream().allMatch(k -> "PASS".equals(checks.get(k))) &&
                List.of("activeLiveness","passiveLiveness","faceMatch").stream().allMatch(k -> Set.of("PASS","DEVELOPMENT_PASS").contains(checks.get(k)));
    }
    /** Canonical report is part of the replay identity, irrespective of JSON key order. */
    public String canonical() {
        return sessionId + ":" + attemptId + ":" + policyVersion + ":" + conclusion + ":" + new TreeMap<>(checks);
    }
}
