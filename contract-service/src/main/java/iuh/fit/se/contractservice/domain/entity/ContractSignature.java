package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_signatures", uniqueConstraints =
        @UniqueConstraint(name = "uk_contract_signer", columnNames = {"contract_id", "account_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractSignature {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "otp_code_hashed", length = 255)
    private String otpCodeHashed;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "otp_sent_at")
    private Instant otpSentAt;

    @Builder.Default
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts = 0;

    @Column(name = "signed_at")
    private Instant signedAt;

    public void sign() {
        if (signedAt != null) {
            throw new IllegalStateException("Contract signature already recorded");
        }
        signedAt = Instant.now();
    }
}
