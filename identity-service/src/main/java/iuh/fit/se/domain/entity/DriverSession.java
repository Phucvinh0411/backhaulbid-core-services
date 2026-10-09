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

/** Code-login driver session. Not an Account: no phone, password or profile. */
@Entity
@Table(name = "driver_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverSession {
    @Id
    private UUID id;
    @Column(name = "grant_id", nullable = false, unique = true)
    private UUID grantId;
    @Column(name = "trip_id", nullable = false)
    private UUID tripId;
    @Column(name = "driver_profile_id", nullable = false)
    private UUID driverProfileId;
    @Column(name = "assignment_version", nullable = false)
    private long assignmentVersion;
    @Column(name = "redeem_request_id", nullable = false)
    private UUID redeemRequestId;
    @Column(name = "refresh_hash", nullable = false, length = 64)
    private String refreshHash;
    @Column(name = "previous_refresh_hash", length = 64)
    private String previousRefreshHash;
    @Column(name = "rotated_at")
    private Instant rotatedAt;
    @Column(name = "replay_cipher", columnDefinition = "bytea")
    private byte[] replayCipher;
    @Column(name = "replay_expires_at")
    private Instant replayExpiresAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public String toString() {
        return "DriverSession(" + id + ")";
    }
}
