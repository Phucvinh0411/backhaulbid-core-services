package iuh.fit.se.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.domain.dto.request.AdminSettingsRequest;
import iuh.fit.se.domain.dto.response.AdminSettingsResponse;
import iuh.fit.se.domain.entity.AdminSetting;
import iuh.fit.se.repository.AdminSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class AdminSettingsService {
    private static final Set<String> SUPPORTED_SCOPES = Set.of(
            "general", "auction", "payment", "notification"
    );

    private final AdminSettingRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminSettingsResponse get(String scope) {
        String normalizedScope = normalizeScope(scope);
        return repository.findByScope(normalizedScope)
                .map(this::toResponse)
                .orElseGet(() -> new AdminSettingsResponse(
                        normalizedScope,
                        new LinkedHashMap<>(),
                        null
                ));
    }

    @Transactional
    public AdminSettingsResponse save(UUID adminId, String scope, AdminSettingsRequest request) {
        String normalizedScope = normalizeScope(scope);
        try {
            AdminSetting setting = repository.findByScope(normalizedScope)
                    .orElseGet(() -> AdminSetting.builder().scope(normalizedScope).build());
            setting.setSettingsJson(objectMapper.writeValueAsString(request.getValues()));
            setting.setUpdatedBy(adminId);
            return toResponse(repository.save(setting));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize admin settings", exception);
        }
    }

    private AdminSettingsResponse toResponse(AdminSetting setting) {
        try {
            Map<String, Object> values = objectMapper.readValue(
                    setting.getSettingsJson(),
                    new TypeReference<>() {}
            );
            return new AdminSettingsResponse(setting.getScope(), values, setting.getUpdatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored admin settings are invalid", exception);
        }
    }

    private String normalizeScope(String scope) {
        String normalizedScope = scope == null ? "" : scope.trim().toLowerCase();
        if (!SUPPORTED_SCOPES.contains(normalizedScope)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported admin settings scope");
        }
        return normalizedScope;
    }
}
