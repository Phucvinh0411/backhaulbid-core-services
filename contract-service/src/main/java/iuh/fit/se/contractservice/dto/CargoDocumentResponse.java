package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentSource;

import java.time.Instant;
import java.util.UUID;

/** One cargo document as the trip's shipper and admins see it: kind, source, time, and a proxy link. Never the key. */
public record CargoDocumentResponse(
        UUID id,
        CargoDocumentKind kind,
        CargoDocumentSource source,
        Instant uploadedAt,
        String fileLink
) {
}
