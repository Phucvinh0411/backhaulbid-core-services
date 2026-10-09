package iuh.fit.se.contractservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GpsBatchRequest(@NotNull UUID trackingSessionId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Point> points) {
    public record Point(@NotNull UUID sampleId, @NotNull Instant capturedAt,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @DecimalMin("0") @DecimalMax("10000") Double accuracyMeters) {}
}
