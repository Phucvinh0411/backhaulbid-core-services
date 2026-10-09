package iuh.fit.se.service;

import java.util.Arrays;
import java.util.Map;

/** Scoped verified upload material; only photos are retained until encrypted persistence completes. */
public record VerifiedNativeEvidence(String digest, NfcIdentitySummary summary, Map<String, byte[]> photos)
        implements AutoCloseable {
    /** Clears retained photo byte arrays after encrypted persistence finishes. */
    @Override public void close() { photos.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0)); }

    /** Redacts evidence fields from diagnostic string output. */
    @Override public String toString() { return "VerifiedNativeEvidence(<redacted>)"; }
}
