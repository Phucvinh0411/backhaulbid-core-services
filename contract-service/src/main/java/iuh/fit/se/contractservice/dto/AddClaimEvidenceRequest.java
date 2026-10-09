package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.ClaimEvidenceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Reference to a file the shipper already uploaded to the private claim-evidence folder. */
public record AddClaimEvidenceRequest(
        @NotNull ClaimEvidenceKind kind,
        @NotBlank @Size(max = 500) String fileUrl
) {
}
