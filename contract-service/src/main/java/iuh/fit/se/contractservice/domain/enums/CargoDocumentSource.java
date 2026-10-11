package iuh.fit.se.contractservice.domain.enums;

/** Where a trip's cargo document came from. Award snapshot rows are never edited after the award. */
public enum CargoDocumentSource {
    /** Copied from the auction at the award attempt that created the trip. */
    AWARD_SNAPSHOT,
    /** Added by the shipper after the award, with the time and the account that added it. */
    TRIP_SUPPLEMENT
}
