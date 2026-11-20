package iuh.fit.se.contractservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The pickup handover record as claim context: what was loaded and in what condition before transport. It is not
 * evidence of damage that happened later. Photos are proxy paths; the storage key is never returned.
 */
public record HandoverContextResponse(
        Instant recordedAt,
        String recordedByType,
        String cargoCategory,
        Integer packageCount,
        BigDecimal grossWeightKg,
        String conditionStatus,
        String conditionNote,
        String sealNumber,
        String placeNote,
        Instant shipperConfirmedAt,
        int photoCount,
        List<String> photoLinks
) {
}
