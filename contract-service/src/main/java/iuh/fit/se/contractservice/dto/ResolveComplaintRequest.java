package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.ComplaintDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResolveComplaintRequest(
        @NotNull ComplaintDecision decision,
        @NotBlank String resolution
) {
}
