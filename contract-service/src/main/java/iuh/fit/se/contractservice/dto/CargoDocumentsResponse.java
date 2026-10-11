package iuh.fit.se.contractservice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The trip's declared goods value and cargo documents. {@code snapshotRecorded} is false for a trip created before cargo
 * snapshots existed: such a trip shows no award documents until the shipper adds some.
 */
public record CargoDocumentsResponse(
        BigDecimal declaredGoodsValue,
        boolean snapshotRecorded,
        boolean valueProofAvailable,
        List<CargoDocumentResponse> documents
) {
}
