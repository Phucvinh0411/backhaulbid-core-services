package iuh.fit.se.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessVerificationReviewRequest(
        @NotBlank String decision,
        @Size(max = 500) String rejectionReason) {
}
