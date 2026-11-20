package iuh.fit.se.contractservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The handover as seen by the trip's shipper, carrier, driver session or an admin. Has no insurance or invoice data.
 * Photos come back as paths under this trip's handover, read through the same session; storage keys never leave
 * the service.
 */
public record TripHandoverResponse(
        UUID tripId,
        String cargoCategory,
        Integer packageCount,
        BigDecimal grossWeightKg,
        String conditionStatus,
        String conditionNote,
        String sealNumber,
        String placeNote,
        List<String> photoPaths,
        String recordedByType,
        Instant recordedAt,
        String shipperSignerName,
        Instant shipperConfirmedAt,
        boolean readyForPickup
) {
}
