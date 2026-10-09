package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;

public record AddressRequest(
        @NotBlank(message = "Address label is required")
        @Size(max = 120, message = "Address label must not exceed 120 characters")
        String label,

        @NotBlank(message = "Contact name is required")
        @Size(max = 150, message = "Contact name must not exceed 150 characters")
        String contactName,

        @NotBlank(message = "Contact phone is required")
        @Pattern(regexp = "[0-9 .()+-]{8,30}", message = "Contact phone is invalid")
        String contactPhone,

        @NotBlank(message = "Province is required")
        @Size(max = 120, message = "Province must not exceed 120 characters")
        String province,

        @NotBlank(message = "Address detail is required")
        @Size(max = 500, message = "Address detail must not exceed 500 characters")
        String detail,
        Double latitude,
        Double longitude,
        @Pattern(regexp = "USER_CONFIRMED", message = "Coordinate source must be USER_CONFIRMED")
        String coordinateSource,
        Instant coordinateConfirmedAt
) {
    public AddressRequest(String label, String contactName, String contactPhone, String province, String detail) {
        this(label, contactName, contactPhone, province, detail, null, null, null, null);
    }

    @AssertTrue(message = "Coordinates must be a complete pair within latitude/longitude bounds")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isCoordinatePairValid() {
        if (latitude == null && longitude == null) return coordinateSource == null && coordinateConfirmedAt == null;
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
