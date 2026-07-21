package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.CompanyStatus;
import iuh.fit.se.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id", unique = true)
    private Account account;

    @Column(unique = true, nullable = false)
    private String taxCode;

    private String companyName;
    private String address;
    private String businessLicenseUrl;
    private String transportLicenseUrl;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    private CompanyStatus companyStatus;

    // Quan hệ 1-Nhiều với InsuranceInfo
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InsuranceInfo> insuranceInfos;
}
