package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "insurance_infos")
@Data
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

    private String providerName;
    private String policyNumber;

    private BigDecimal coverageLimit; // Dùng BigDecimal cho tiền tệ thay vì Decimal
    private LocalDate expiredDate;

    @Enumerated(EnumType.STRING)
    private VerificationStatus status;
}
