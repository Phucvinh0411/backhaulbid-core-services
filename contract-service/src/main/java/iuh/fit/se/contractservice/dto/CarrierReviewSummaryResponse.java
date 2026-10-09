package iuh.fit.se.contractservice.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Review summary for one carrier. {@code averageRating} is null when there are no reviews, so the UI
 * can show "Chưa có đánh giá" instead of a zero. {@code distribution} always has keys 1 to 5.
 */
public record CarrierReviewSummaryResponse(
        UUID carrierId,
        Double averageRating,
        long reviewCount,
        Map<Integer, Long> distribution
) {
}
