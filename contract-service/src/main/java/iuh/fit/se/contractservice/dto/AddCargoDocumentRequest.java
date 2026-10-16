package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A file the shipper uploaded to the private goods-value-docs folder, with its kind. Only the key is accepted. */
public record AddCargoDocumentRequest(
        @NotBlank @Size(max = 500) String key,
        @NotNull CargoDocumentKind kind
) {
}
