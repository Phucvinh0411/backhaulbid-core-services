package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.CompanyStatus;
import iuh.fit.se.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "id", nullable = false, unique = true)
    private Account account;

    @Column(unique = true, nullable = false, length = 50)
    private String taxCode;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(length = 255)
    private String address;

    @Column(name = "legal_representative", length = 255)
    private String legalRepresentative;

    @Column(name = "ekyc_representative_name", length = 255)
    private String ekycRepresentativeName;

    @Column(name = "representative_matched")
    private Boolean representativeMatched;

    @Column(name = "requires_authorization")
    private Boolean requiresAuthorization;

    @Column(name = "business_license_url", length = 500)
    private String businessLicenseUrl;

    @Column(name = "business_license_filename", length = 255)
    private String businessLicenseFilename;

    @Column(name = "authorization_letter_url", length = 500)
    private String authorizationLetterUrl;

    @Column(name = "authorization_letter_filename", length = 255)
    private String authorizationLetterFilename;

    @Column(name = "transport_license_url", length = 500)
    private String transportLicenseUrl;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 50)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_status", length = 50)
    private CompanyStatus companyStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Quan hệ 1-Nhiều với InsuranceInfo
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InsuranceInfo> insuranceInfos;
}
