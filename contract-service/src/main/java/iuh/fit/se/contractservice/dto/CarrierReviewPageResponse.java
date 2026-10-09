package iuh.fit.se.contractservice.dto;

import java.util.List;

/** One page of a carrier's reviews, newest first. Page numbers are 1-based. */
public record CarrierReviewPageResponse(
        List<CarrierReviewItemResponse> items,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
