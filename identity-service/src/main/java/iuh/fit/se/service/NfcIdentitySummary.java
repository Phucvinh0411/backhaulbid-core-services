package iuh.fit.se.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DG1 fields plus the DG13 citizen ID once DG13 is verified. The MRZ document number is not the full national
 * identity number; {@code citizenId} carries the 12-digit number from DG13 and is null when DG13 did not supply it.
 * Older stored summaries may still carry a residence field; it is ignored on read.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NfcIdentitySummary(String fullName, String documentNumber, String dateOfBirth,
        String dateOfExpiry, String sex, String nationality, String issuingCountry, String source,
        String citizenId) {
    /** Returns this summary with the citizen ID read from the front image, once the caller has validated it. */
    public NfcIdentitySummary withCitizenId(String citizenId) {
        return new NfcIdentitySummary(fullName, documentNumber, dateOfBirth, dateOfExpiry, sex, nationality,
                issuingCountry, source, citizenId);
    }

    /** Redacts chip fields from diagnostic string output. */
    @Override public String toString() { return "NfcIdentitySummary(<redacted>)"; }
}
