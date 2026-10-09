package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;
import iuh.fit.se.contractservice.domain.enums.InsuranceClaimStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A shipper's claim draft for one trip. Its status is a preparation state; see {@link InsuranceClaimStatus}. */
@Entity
@Table(name = "insurance_claims")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceClaim {
    /** UUIDv7 assigned by the service. */
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 20)
    private ClaimIncidentType incidentType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "claimed_amount", precision = 14, scale = 2)
    private BigDecimal claimedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InsuranceClaimStatus status;

    @Column(name = "provider_reference", length = 80)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
