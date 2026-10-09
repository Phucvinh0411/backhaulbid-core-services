package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.dto.CarrierReviewPageResponse;
import iuh.fit.se.contractservice.dto.CarrierReviewSummaryResponse;
import iuh.fit.se.contractservice.service.TripReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Public-to-members review summary and list for a carrier. Gateway routes {@code /api/v1/carrier-reviews/**} here. */
@RestController
@RequestMapping("/api/v1/carrier-reviews")
@RequiredArgsConstructor
public class CarrierReviewController {
    private final TripReviewService reviews;

    @GetMapping("/{carrierId}/summary")
    public CarrierReviewSummaryResponse summary(@RequestHeader("X-User-Role") String role,
                                                @PathVariable UUID carrierId) {
        return reviews.summary(TripReviewController.parseRole(role), carrierId);
    }

    @GetMapping("/{carrierId}")
    public CarrierReviewPageResponse page(@RequestHeader("X-User-Role") String role,
                                          @PathVariable UUID carrierId,
                                          @RequestParam(name = "page", defaultValue = "1") int page,
                                          @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return reviews.page(TripReviewController.parseRole(role), carrierId, page, pageSize);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> businessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", Objects.requireNonNullElse(exception.getReason(), "Request could not be completed")));
    }
}
