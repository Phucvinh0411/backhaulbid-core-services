package iuh.fit.se.domain.dto.response;

import java.time.Instant;
import java.util.Map;

public record AdminSettingsResponse(
        String scope,
        Map<String, Object> values,
        Instant updatedAt
) {
}
