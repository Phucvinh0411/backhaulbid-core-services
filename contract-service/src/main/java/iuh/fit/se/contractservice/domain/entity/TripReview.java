package iuh.fit.se.contractservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The shipper's rating of a completed trip. Kept apart from the fleet reputation score: a review
 * never changes reputation, and reputation never changes a review.
 */
@Entity
@Table(name = "trip_reviews")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripReview {
    /** UUIDv7 assigned by the service (not a database default). */
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private UUID tripId;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    /** 1 to 5 stars; the database enforces the same range. */
    @Column(nullable = false)
    private Integer rating;

    /** Optional free text, trimmed, at most 1000 characters. */
    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
