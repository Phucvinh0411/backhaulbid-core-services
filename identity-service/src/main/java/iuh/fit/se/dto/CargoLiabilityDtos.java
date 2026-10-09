package iuh.fit.se.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for a carrier's cargo-liability certificate. */
public final class CargoLiabilityDtos {

    private CargoLiabilityDtos() {
    }

    /** certificateKey is the media key returned by the upload, never a public URL. */
    public record SubmitRequest(
            @NotBlank @Size(max = 255) String providerName,
            @NotBlank @Size(max = 100) String policyNumber,
            @DecimalMin(value = "0.01") BigDecimal coverageLimit,
            @NotNull LocalDate expiredDate,
            @NotBlank @Size(max = 500) String certificateKey) {
    }

    public record Submission(
            UUID id,
            String status,
            String providerName,
            String policyNumber,
            BigDecimal coverageLimit,
            LocalDate expiredDate,
            Instant submittedAt,
            String rejectionReason) {
    }

    /** eligible and verifiedUntil come from qualifying rows only; latest is the newest submission, whatever its state. */
    public record Status(boolean eligible, LocalDate verifiedUntil, Submission latest) {
    }

    public record ReviewRequest(
            @NotBlank String decision,
            @Size(max = 500) String rejectionReason) {
    }

    public record ReviewItem(
            UUID id,
            UUID accountId,
            String companyName,
            String taxCode,
            String providerName,
            String policyNumber,
            BigDecimal coverageLimit,
            LocalDate expiredDate,
            Instant submittedAt,
            String status,
            String rejectionReason) {
    }

    public record ReviewPage(List<ReviewItem> items, int page, int pageSize, long totalItems, int totalPages) {
    }

    public record Eligibility(boolean eligible) {
    }
}
