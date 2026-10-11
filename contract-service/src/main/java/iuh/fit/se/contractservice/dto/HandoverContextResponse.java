package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripHandover;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    /** The shipper's view: photo proxy paths included. */
    public static HandoverContextResponse of(TripHandover handover, UUID tripId, boolean withPhotoLinks) {
        List<String> links = withPhotoLinks
                ? java.util.stream.IntStream.range(0, handover.getPhotoKeys().size())
                        .mapToObj(index -> "/api/v1/trips/" + tripId + "/handover/photos/" + index)
                        .toList()
                : List.of();
        return new HandoverContextResponse(handover.getRecordedAt(), handover.getRecordedByType(),
                handover.getCargoCategory(), handover.getPackageCount(), handover.getGrossWeightKg(),
                handover.getConditionStatus(), handover.getConditionNote(), handover.getSealNumber(),
                handover.getPlaceNote(), handover.getShipperConfirmedAt(), handover.getPhotoKeys().size(), links);
    }
}
