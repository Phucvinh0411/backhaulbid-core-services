package iuh.fit.se.contractservice.domain.enums;

/**
 * Kind of a shipper's proof of the goods' declared value. The value-proof kinds are the ones the carrier-coverage
 * checklist counts; OTHER and UNCLASSIFIED never do. UNCLASSIFIED is what an auction saved before kinds existed reads as.
 */
public enum CargoDocumentKind {
    INVOICE,
    RECEIPT,
    CONTRACT,
    PAYMENT_PROOF,
    OTHER,
    UNCLASSIFIED;

    /** Whether this kind proves the goods' value. Keep in step with bidding-service's VALUE_PROOF_KINDS. */
    public boolean isValueProof() {
        return this == INVOICE || this == RECEIPT || this == CONTRACT || this == PAYMENT_PROOF;
    }
}
