package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.InsuranceCoverageType;
import iuh.fit.se.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "insurance_infos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "provider_name", length = 255)
    private String providerName;

    @Column(name = "policy_number", length = 100)
    private String policyNumber;

    @Column(name = "coverage_limit", precision = 15, scale = 2)
    private BigDecimal coverageLimit;

    @Column(name = "expired_date")
    private LocalDate expiredDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private VerificationStatus status;

    /** Null for rows entered before cargo-liability certificates existed; those never satisfy a requirement. */
    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_type", length = 40)
    private InsuranceCoverageType coverageType;

    /** Object key of the certificate in the private business-verifications/insurance folder. Never a public URL. */
    @Column(name = "certificate_key", length = 500)
    private String certificateKey;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Admin who reviewed the certificate. Set for both decisions; a row qualifies only when it is VERIFIED. */
    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
