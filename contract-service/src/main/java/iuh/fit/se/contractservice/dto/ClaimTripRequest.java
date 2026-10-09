package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaimTripRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "PIN must contain exactly six digits") String pin
) {}
