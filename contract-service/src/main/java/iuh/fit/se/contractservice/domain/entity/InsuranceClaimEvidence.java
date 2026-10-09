package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A reference to one private evidence file. The object key (claim-evidence/uuid.ext) stays server-side; clients get a proxy link. */
@Entity
@Table(name = "insurance_claim_evidence")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceClaimEvidence {
    /** UUIDv7 assigned by the service. */
    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private ClaimEvidenceKind kind;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
