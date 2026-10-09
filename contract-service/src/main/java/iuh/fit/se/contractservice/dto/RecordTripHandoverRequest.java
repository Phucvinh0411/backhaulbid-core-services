package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Handover record written by the driver (or carrier) before pickup. Fields that do not apply to a load
 * (package count, seal, place note) are optional; a DAMAGED condition must carry a note (checked in the service).
 * {@code photoUrls} takes an uploaded photo's key or URL, or a photo path the record already has (when editing).
 */
public record RecordTripHandoverRequest(
        @NotBlank @Size(max = 80) String cargoCategory,
        @Min(1) Integer packageCount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal grossWeightKg,
        @NotNull @Pattern(regexp = "GOOD|DAMAGED") String conditionStatus,
        @Size(max = 500) String conditionNote,
        @Size(max = 60) String sealNumber,
        @Size(max = 255) String placeNote,
        @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 500) String> photoUrls
) {
}
