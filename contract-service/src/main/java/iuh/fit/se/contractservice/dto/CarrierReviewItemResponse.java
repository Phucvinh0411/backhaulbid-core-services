package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripReview;

import java.time.Instant;
import java.util.UUID;

/**
 * A review shown on a carrier's public profile. Carries no trip, shipper, contact or eKYC data:
 * the reviewer is anonymous ("Chủ hàng đã xác nhận giao hàng") by design.
 */
public record CarrierReviewItemResponse(
        UUID id,
        int rating,
        String comment,
        Instant createdAt
) {
    public static CarrierReviewItemResponse from(TripReview review) {
        return new CarrierReviewItemResponse(review.getId(), review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
