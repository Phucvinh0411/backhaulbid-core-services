package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateComplaintRequest(
        @NotNull UUID tripId,
        @NotBlank String title,
        @NotBlank String description,
        @Size(max = 500) String evidenceUrl
) {
    public CreateComplaintRequest(UUID tripId, String title, String description) {
        this(tripId, title, description, null);
    }
}
