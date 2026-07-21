package iuh.fit.se.domain.entity;

import iuh.fit.se.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "ekyc_verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EkycVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id", unique = true)
    private Account account;

    private String identityNumber; // Số CCCD
    private String frontImageUrl;
    private String backImageUrl;
    private String selfieImageUrl;
    private String livenessVideoUrl;

    private Double faceMatchScore;
    private Boolean livenessPassed;

    @Enumerated(EnumType.STRING)
    private VerificationStatus status;
}
