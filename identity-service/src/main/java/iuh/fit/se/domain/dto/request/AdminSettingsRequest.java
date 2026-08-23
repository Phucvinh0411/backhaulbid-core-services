package iuh.fit.se.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AdminSettingsRequest {
    @NotEmpty
    private Map<String, Object> values = new LinkedHashMap<>();
}
