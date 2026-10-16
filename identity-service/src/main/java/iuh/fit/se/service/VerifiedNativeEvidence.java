package iuh.fit.se.service;

import java.util.Arrays;
import java.util.Map;

/** Scoped verified upload material; only photos are retained until encrypted persistence completes. */
public record VerifiedNativeEvidence(String digest, NfcIdentitySummary summary, Map<String, byte[]> photos)
        implements AutoCloseable {
    /** Clears retained photo byte arrays after encrypted persistence finishes. */
    @Override public void close() { photos.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0)); }

    /** Returns this evidence with the citizen ID read from the front image, kept only when it is exactly twelve digits. */
    public VerifiedNativeEvidence withCitizenIdFromImage(String citizenId) {
        String valid = citizenId != null && citizenId.matches("\\d{12}") ? citizenId : null;
        return valid == null ? this : new VerifiedNativeEvidence(digest, summary.withCitizenId(valid), photos);
    }

    /** Redacts evidence fields from diagnostic string output. */
    @Override public String toString() { return "VerifiedNativeEvidence(<redacted>)"; }
}
