package iuh.fit.se.fleetservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDriverRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 50) String licenseNumber,
        @Size(max = 500) String licenseImageUrl
) {
}
