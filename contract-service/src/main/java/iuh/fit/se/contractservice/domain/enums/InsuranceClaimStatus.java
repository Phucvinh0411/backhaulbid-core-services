package iuh.fit.se.contractservice.domain.enums;

/**
 * Claim lifecycle. The shipper's and carrier's own actions write only DRAFT, NEEDS_EVIDENCE, READY_TO_SHARE,
 * SHARED_WITH_CARRIER and CARRIER_ACKNOWLEDGED. READY_TO_SHARE means the checklist is complete and the claim may be
 * shared with the trip's carrier; it does not mean it was sent to anyone. Sharing and acknowledgement describe the
 * platform's hand-over to the carrier, never a provider's answer. The provider states are reserved for a confirmed
 * provider response and are never set from these actions.
 */
public enum InsuranceClaimStatus {
    DRAFT,
    NEEDS_EVIDENCE,
    READY_TO_SHARE,
    SHARED_WITH_CARRIER,
    CARRIER_ACKNOWLEDGED,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    DENIED,
    CLOSED
}
