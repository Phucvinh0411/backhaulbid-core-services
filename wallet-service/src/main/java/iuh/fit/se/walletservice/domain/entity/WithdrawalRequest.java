package iuh.fit.se.walletservice.domain.entity;

import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Manual withdrawal request. Bank details are kept for admin processing and
 * masked by the mapper before they leave the service boundary.
 */
@Entity
@Table(name = "withdrawal_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_account_number", nullable = false, length = 30)
    private String bankAccountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 120)
    private String accountHolderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
