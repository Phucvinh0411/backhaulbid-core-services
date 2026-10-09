package iuh.fit.se.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** Audit metadata only: no names, document numbers, images, or raw parser data. */
@Entity @Table(name="ekyc_review_audit") @Getter @Setter
public class EkycReviewAudit {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    private UUID reportId;
    @Column(nullable=false) private UUID actorId;
    @Column(nullable=false, length=32) private String action;
    @Column(nullable=false) private Instant occurredAt;
}
