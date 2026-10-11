package iuh.fit.se.contractservice.domain.entity;

import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One proof of the goods' declared value that belongs to a trip. The object key stays on the server: responses carry
 * the kind, the time and a proxy path. Rows of AWARD_SNAPSHOT are written once, with the trip.
 */
@Entity
@Table(name = "trip_cargo_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCargoDocument {
    /** UUIDv7 assigned by the service. This is the id clients use in the proxy path. */
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    /** The file's UUID, taken from its object key. */
    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private CargoDocumentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CargoDocumentSource source;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
