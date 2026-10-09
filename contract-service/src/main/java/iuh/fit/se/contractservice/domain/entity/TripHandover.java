package iuh.fit.se.contractservice.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The goods-handover record taken at pickup: what was loaded, its condition, photos, and the shipper's
 * confirmation. Only the handover's own fields are here; invoices and insurance values are kept elsewhere
 * and are never shown to the driver.
 */
@Entity
@Table(name = "trip_handovers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripHandover {
    public static final String GOOD = "GOOD";
    public static final String DAMAGED = "DAMAGED";
    public static final String RECORDED_BY_DRIVER = "DRIVER";
    public static final String RECORDED_BY_CARRIER = "CARRIER";

    /** UUIDv7 assigned by the service. */
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private UUID tripId;

    @Column(name = "cargo_category", nullable = false, length = 80)
    private String cargoCategory;

    @Column(name = "package_count")
    private Integer packageCount;

    @Column(name = "gross_weight_kg", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossWeightKg;

    @Column(name = "condition_status", nullable = false, length = 20)
    private String conditionStatus;

    @Column(name = "condition_note", length = 500)
    private String conditionNote;

    @Column(name = "seal_number", length = 60)
    private String sealNumber;

    @Column(name = "place_note", length = 255)
    private String placeNote;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_by_type", nullable = false, length = 10)
    private String recordedByType;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "shipper_signer_name", length = 120)
    private String shipperSignerName;

    @Column(name = "shipper_confirmed_by")
    private UUID shipperConfirmedBy;

    @Column(name = "shipper_confirmed_at")
    private Instant shipperConfirmedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Photo object keys in the private handover-photos folder (folder/uuid.ext), in position order. Storage URLs are
     * never kept here; clients read photos through the handover's proxy path.
     */
    @ElementCollection
    @CollectionTable(name = "trip_handover_photos", joinColumns = @JoinColumn(name = "handover_id"))
    @OrderColumn(name = "position")
    @Column(name = "object_key", nullable = false, length = 500)
    @Builder.Default
    private List<String> photoKeys = new ArrayList<>();

    /** Pickup may start only with at least one photo and the shipper's confirmation. */
    public boolean isReadyForPickup() {
        return shipperConfirmedAt != null && photoKeys != null && !photoKeys.isEmpty();
    }
}
