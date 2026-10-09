package iuh.fit.se.contractservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The shipper's confirmation that the goods were handed over; the typed name is kept as the record of who signed. */
public record ConfirmTripHandoverRequest(
        @NotBlank @Size(max = 120) String signerName
) {
}
