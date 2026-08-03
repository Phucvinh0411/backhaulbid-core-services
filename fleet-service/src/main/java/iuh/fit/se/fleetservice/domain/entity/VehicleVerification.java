package iuh.fit.se.fleetservice.domain.entity;

import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_verifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, unique = true)
    private Vehicle vehicle;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void approve(UUID adminId) {
        verifiedBy = adminId;
        status = VerificationStatus.VERIFIED;
        note = null;
        verifiedAt = Instant.now();
    }

    public void reject(UUID adminId, String reason) {
        verifiedBy = adminId;
        status = VerificationStatus.REJECTED;
        note = reason;
        verifiedAt = Instant.now();
    }
}
