package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
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
    public static CargoDocumentResponse from(TripCargoDocument document) {
        return new CargoDocumentResponse(document.getId(), document.getKind(), document.getSource(),
                document.getUploadedAt(),
                "/api/v1/trips/" + document.getTripId() + "/cargo-documents/" + document.getId() + "/file");
    }
}
