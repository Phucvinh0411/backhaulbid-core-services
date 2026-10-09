package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripReview;

import java.time.Instant;
import java.util.UUID;

/** A review as seen by the trip's own shipper, carrier or an admin. */
public record TripReviewResponse(
        UUID id,
        UUID tripId,
        UUID carrierId,
        int rating,
        String comment,
        Instant createdAt
) {
    public static TripReviewResponse from(TripReview review) {
        return new TripReviewResponse(review.getId(), review.getTripId(), review.getCarrierId(),
                review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
