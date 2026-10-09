package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.ClaimIncidentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** A claim draft for one trip. The shipper is taken from the trip; the claim is never sent from here. */
public record CreateInsuranceClaimRequest(
        @NotNull ClaimIncidentType incidentType,
        @NotNull Instant occurredAt,
        @NotBlank @Size(min = 10, max = 1000) String description,
        @DecimalMin("0.01") @DecimalMax("100000000000") BigDecimal claimedAmount
) {
}
