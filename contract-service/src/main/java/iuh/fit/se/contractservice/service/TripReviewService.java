package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripReview;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CarrierReviewItemResponse;
import iuh.fit.se.contractservice.dto.CarrierReviewPageResponse;
import iuh.fit.se.contractservice.dto.CarrierReviewSummaryResponse;
import iuh.fit.se.contractservice.dto.CreateTripReviewRequest;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.TripReviewRepository;
import iuh.fit.se.contractservice.service.driveraccess.UuidV7;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shipper reviews of completed trips and the carrier-level summaries built from them.
 * Reviews are stored separately from fleet reputation and never change it.
 */
@Service
public class TripReviewService {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Comments must not carry contact details: a review is public on the carrier profile. */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(\\+?84|0)[\\s.-]?\\d{1,4}[\\s.-]?\\d{3}[\\s.-]?\\d{3,4}(?!\\d)");

    private final TripRepository trips;
    private final TripReviewRepository reviews;
    private final Clock clock;
    private final UuidV7 ids;

    public TripReviewService(TripRepository trips, TripReviewRepository reviews, Clock clock) {
        this.trips = trips;
        this.reviews = reviews;
        this.clock = clock;
        this.ids = new UuidV7(clock, new SecureRandom());
    }

    /**
     * Records the shipper's review of a trip that reached {@code COMPLETED}. Only the trip's own shipper
     * may write it; the trip row is locked so a status change and a review cannot interleave. The unique
     * constraint on {@code trip_id} turns a duplicate race into a 409.
     */
    @Transactional
    public TripReview create(UUID accountId, AccountRole role, UUID tripId, CreateTripReviewRequest request) {
        if (role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shipper of this trip can review it");
        }
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (!accountId.equals(trip.getShipperId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shipper of this trip can review it");
        }
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ chuyến đã hoàn tất mới được đánh giá. Hãy xác nhận đã nhận hàng trước.");
        }
        if (reviews.findByTripId(tripId).isPresent()) {
            throw alreadyReviewed();
        }
        TripReview review = TripReview.builder()
                .id(ids.next())
                .tripId(tripId)
                .shipperId(trip.getShipperId())
                .carrierId(trip.getCarrierId())
                .rating(request.rating())
                .comment(normalizeComment(request.comment()))
                .createdAt(clock.instant())
                .build();
        try {
            return reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException duplicate) {
            throw alreadyReviewed();
        }
    }

    /** Reviews of one trip, visible to its shipper, its carrier and admins. */
    @Transactional(readOnly = true)
    public List<TripReview> forTrip(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        boolean allowed = role == AccountRole.ADMIN
                || (role == AccountRole.CARRIER && accountId.equals(trip.getCarrierId()))
                || (role == AccountRole.SHIPPER && accountId.equals(trip.getShipperId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }
        return reviews.findByTripId(tripId).map(List::of).orElse(List.of());
    }

    /** Average (null when empty), count and 1-to-5 distribution for a carrier's reviews. */
    @Transactional(readOnly = true)
    public CarrierReviewSummaryResponse summary(AccountRole role, UUID carrierId) {
        requireReader(role);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(stars, 0L);
        }
        long count = 0;
        for (Object[] row : reviews.distributionByCarrier(carrierId)) {
            long n = ((Number) row[1]).longValue();
            distribution.put(((Number) row[0]).intValue(), n);
            count += n;
        }
        Double average = count == 0 ? null : roundToTwo(reviews.averageByCarrier(carrierId));
        return new CarrierReviewSummaryResponse(carrierId, average, count, distribution);
    }

    /** Newest-first page of a carrier's reviews. Page is 1-based; pageSize is 1 to {@link #MAX_PAGE_SIZE}. */
    @Transactional(readOnly = true)
    public CarrierReviewPageResponse page(AccountRole role, UUID carrierId, int page, int pageSize) {
        requireReader(role);
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be at least 1 and pageSize between 1 and " + MAX_PAGE_SIZE);
        }
        Page<TripReview> result = reviews.findByCarrierId(carrierId,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<CarrierReviewItemResponse> items = result.getContent().stream().map(CarrierReviewItemResponse::from).toList();
        return new CarrierReviewPageResponse(items, page, pageSize, result.getTotalElements(), result.getTotalPages());
    }

    /** Null or blank becomes no comment; otherwise trimmed, and rejected when it contains an email or phone number. */
    static String normalizeComment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String comment = raw.trim();
        if (EMAIL.matcher(comment).find() || PHONE.matcher(comment).find()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nhận xét không được chứa email hoặc số điện thoại. Vui lòng bỏ thông tin liên hệ.");
        }
        return comment;
    }

    private static void requireReader(AccountRole role) {
        if (role == AccountRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier reviews are not available to driver sessions");
        }
    }

    private static ResponseStatusException alreadyReviewed() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Chuyến này đã được đánh giá");
    }

    private static double roundToTwo(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
