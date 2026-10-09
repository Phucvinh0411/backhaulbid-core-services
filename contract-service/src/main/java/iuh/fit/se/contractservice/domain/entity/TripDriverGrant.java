package iuh.fit.se.contractservice.domain.entity;

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

/**
 * One driver assignment code. The ID is a UUIDv7 identifier; access requires the random secret whose
 * SHA-256 is stored here. Redeeming binds exactly one driver session to the assignment.
 */
@Entity
@Table(name = "trip_driver_grants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDriverGrant {
    public static final String ISSUED = "ISSUED", REDEEMED = "REDEEMED", REVOKED = "REVOKED";

    @Id
    private UUID id;
    @Column(name = "trip_id", nullable = false)
    private UUID tripId;
    @Column(name = "driver_profile_id", nullable = false)
    private UUID driverProfileId;
    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;
    @Column(name = "assignment_version", nullable = false)
    private long assignmentVersion;
    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;
    @Column(nullable = false, length = 12)
    private String state;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "redeem_request_id")
    private UUID redeemRequestId;
    @Column(name = "driver_session_id", unique = true)
    private UUID driverSessionId;
    @Column(name = "redeemed_at")
    private Instant redeemedAt;
    @Column(name = "session_revoked_at")
    private Instant sessionRevokedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "revoke_reason", length = 40)
    private String revokeReason;
}
