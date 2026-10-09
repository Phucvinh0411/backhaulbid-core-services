package iuh.fit.se.contractservice.domain.enums;

/**
 * Claim lifecycle. This application writes only DRAFT, NEEDS_EVIDENCE and READY_FOR_PROVIDER. The provider
 * states are reserved for a confirmed provider response and are never set from the shipper's own actions.
 */
public enum InsuranceClaimStatus {
    DRAFT,
    NEEDS_EVIDENCE,
    READY_FOR_PROVIDER,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    DENIED,
    CLOSED
}
