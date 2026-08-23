package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        String detail
) {
}
