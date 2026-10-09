package iuh.fit.se.service;

/** Named DG1 fields only. MRZ document number is not claimed to be the full national identity number. */
public record NfcIdentitySummary(String fullName, String documentNumber, String dateOfBirth,
        String dateOfExpiry, String sex, String nationality, String issuingCountry, String source) {
    /** Redacts chip fields from diagnostic string output. */
    @Override public String toString() { return "NfcIdentitySummary(<redacted>)"; }
}
