package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.dto.CreateTripReviewRequest;
import iuh.fit.se.contractservice.dto.TripReviewResponse;
import iuh.fit.se.contractservice.service.TripReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Shipper review of one completed trip. Gateway routes {@code /api/v1/trips/**} here. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}/reviews")
@RequiredArgsConstructor
public class TripReviewController {
    private final TripReviewService reviews;

    /** Creates the shipper's one review of a COMPLETED trip. Responds 201, 403, 404, 409 or 422. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripReviewResponse create(@RequestHeader("X-User-Id") UUID accountId,
                                     @RequestHeader("X-User-Role") String role,
                                     @PathVariable UUID tripId,
                                     @Valid @RequestBody CreateTripReviewRequest request) {
        return TripReviewResponse.from(reviews.create(accountId, parseRole(role), tripId, request));
    }

    /** The trip's review, as an array of zero or one item. */
    @GetMapping
    public List<TripReviewResponse> forTrip(@RequestHeader("X-User-Id") UUID accountId,
                                            @RequestHeader("X-User-Role") String role,
                                            @PathVariable UUID tripId) {
        return reviews.forTrip(accountId, parseRole(role), tripId).stream().map(TripReviewResponse::from).toList();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> businessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Request could not be completed")));
    }

    static AccountRole parseRole(String role) {
        try {
            return AccountRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown account role");
        }
    }
}
