package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.AccountRole;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "ekyc_capture_sessions")
@Getter @Setter @NoArgsConstructor
public class EkycCaptureSession {
    public enum State { CREATED, PAIRED, APPROVED, CAPTURING, SUBMITTED, CANCELLED, EXPIRED }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private AccountRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(length = 64) private String pairingTokenHash;
    @Column(length = 64) private String deviceNonceHash;
    @Column(length = 64) private String uploadGrantHash;
    @Column(length = 6) private String confirmationCode;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant createdAt;
    private UUID attemptId;
    private UUID receiptId;
    @Column(length = 64) private String evidenceDigest;
    @Version private long version;
}
