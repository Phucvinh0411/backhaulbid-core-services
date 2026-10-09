package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.ComplaintCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateComplaintRequest(
        @NotNull UUID tripId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @Size(max = 500) String evidenceUrl,
        @NotNull ComplaintCategory category
) {
}
