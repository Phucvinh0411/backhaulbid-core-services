package iuh.fit.se.domain.entity;

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
import java.util.UUID;

@Entity
@Table(name = "ekyc_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EkycVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "id", nullable = false, unique = true)
    private Account account;

    @Column(name = "identity_number", length = 50)
    private String identityNumber;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "front_image_url", length = 500)
    private String frontImageUrl;

    @Column(name = "back_image_url", length = 500)
    private String backImageUrl;

    @Column(name = "selfie_image_url", length = 500)
    private String selfieImageUrl;

    @Column(name = "liveness_video_url", length = 500)
    private String livenessVideoUrl;

    private Double faceMatchScore;
    private Boolean ocrPassed;
    private Boolean documentLivenessPassed;
    private Boolean documentAuthenticityPassed;
    private Boolean livenessPassed;
    private Boolean faceMatched;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private VerificationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
