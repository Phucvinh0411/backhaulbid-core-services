package iuh.fit.se.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pending email-change OTP challenge stored per account for multi-pod verification. */
@Entity
@Table(name = "email_change_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailChangeOtp {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "pending_email", nullable = false, length = 255)
    private String pendingEmail;

    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;
}
