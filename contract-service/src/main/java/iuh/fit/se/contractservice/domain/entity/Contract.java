package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "contract_code", nullable = false, unique = true, length = 50)
    private String contractCode;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "pdf_hash_sha256", length = 64)
    private String pdfHashSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;

    @Builder.Default
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractSignature> signatures = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addSignature(ContractSignature signature) {
        if (signature == null) {
            throw new IllegalArgumentException("Signature is required");
        }
        if (signatures == null) {
            signatures = new ArrayList<>();
        }
        signature.setContract(this);
        signatures.add(signature);
    }

    public boolean isFullySigned() {
        if (signatures == null) {
            return false;
        }
        boolean shipperSigned = signatures.stream()
                .anyMatch(signature -> isSignedBy(signature, AccountRole.SHIPPER));
        boolean carrierSigned = signatures.stream()
                .anyMatch(signature -> isSignedBy(signature, AccountRole.CARRIER));
        return shipperSigned && carrierSigned;
    }

    public void markSigned() {
        if (!isFullySigned()) {
            throw new IllegalStateException("Both shipper and carrier must sign the contract");
        }
        status = ContractStatus.SIGNED;
    }

    public boolean verifyIntegrity(String currentHash) {
        if (pdfHashSha256 == null || currentHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                pdfHashSha256.getBytes(StandardCharsets.US_ASCII),
                currentHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean isSignedBy(ContractSignature signature, AccountRole role) {
        return signature.getRole() == role && signature.getSignedAt() != null;
    }
}
