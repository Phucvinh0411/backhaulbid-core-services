package iuh.fit.se.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="ekyc_review_reports") @Getter @Setter
public class EkycReviewReport {
    @Id private UUID id;
    @Column(nullable=false) private UUID accountId;
    @Column(nullable=false, length=24) private String status;
    @Column(nullable=false) private Instant submittedAt;
    @Column(nullable=false) private Instant evidenceExpiresAt;
    private Instant reviewedAt;
    private UUID reviewedBy;
    @Column(length=500) private String reviewReason;
    @Column(nullable=false, length=40) private String verificationMethod;
    @Column(nullable=false, length=24) private String captureConclusion;
    @Column(nullable=false, columnDefinition="text") private String sdkChecks;
    private boolean documentCompared;
    private boolean faceCompared;
    @Column(nullable=false, columnDefinition="bytea") private byte[] summaryEncrypted;
    @Column(columnDefinition="bytea") private byte[] frontEncrypted;
    @Column(columnDefinition="bytea") private byte[] backEncrypted;
    @Column(columnDefinition="bytea") private byte[] selfieEncrypted;
    @Version private long version;
    @Override public String toString() { return "EkycReviewReport(<redacted>)"; }
}
