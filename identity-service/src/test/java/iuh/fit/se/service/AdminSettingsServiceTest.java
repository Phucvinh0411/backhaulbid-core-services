package iuh.fit.se.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.domain.dto.request.AdminSettingsRequest;
import iuh.fit.se.domain.entity.AdminSetting;
import iuh.fit.se.repository.AdminSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {
    @Mock
    private AdminSettingRepository repository;

    private AdminSettingsService service;

    @BeforeEach
    void setUp() {
        service = new AdminSettingsService(repository, new ObjectMapper());
    }

    @Test
    void returnsEmptyValuesWhenScopeHasNotBeenSaved() {
        when(repository.findByScope("general")).thenReturn(Optional.empty());

        var result = service.get("GENERAL");

        assertThat(result.scope()).isEqualTo("general");
        assertThat(result.values()).isEmpty();
    }

    @Test
    void savesAndReadsStructuredSettings() {
        UUID adminId = UUID.randomUUID();
        AdminSettingsRequest request = new AdminSettingsRequest();
        request.setValues(new LinkedHashMap<>(Map.of("platformName", "BackHaulBid", "maintenanceMode", true)));
        when(repository.findByScope("general")).thenReturn(Optional.empty());
        when(repository.save(any(AdminSetting.class))).thenAnswer(invocation -> {
            AdminSetting setting = invocation.getArgument(0);
            setting.setUpdatedAt(java.time.Instant.now());
            return setting;
        });

        var saved = service.save(adminId, "general", request);

        assertThat(saved.values()).containsEntry("platformName", "BackHaulBid");
        assertThat(saved.values()).containsEntry("maintenanceMode", true);
    }

    @Test
    void get_unsupportedScope_returns400() {
        assertThatThrownBy(() -> service.get("users"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Unsupported admin settings scope");
    }

    @Test
    void save_existingSetting_updatesValuesAndAdmin() {
        UUID adminId = UUID.randomUUID();
        AdminSetting existing = AdminSetting.builder()
                .scope("general")
                .settingsJson("{\"platformName\":\"Old\"}")
                .build();
        AdminSettingsRequest request = new AdminSettingsRequest();
        request.setValues(new LinkedHashMap<>(Map.of("platformName", "New")));
        when(repository.findByScope("general")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var actualResponse = service.save(adminId, "general", request);

        assertThat(actualResponse.values()).containsEntry("platformName", "New");
        assertThat(existing.getUpdatedBy()).isEqualTo(adminId);
        assertThat(existing.getSettingsJson()).contains("New");
    }

    @Test
    void get_corruptStoredJson_throwsIllegalStateException() {
        AdminSetting setting = AdminSetting.builder()
                .scope("general")
                .settingsJson("not-json")
                .build();
        when(repository.findByScope("general")).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> service.get("general"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stored admin settings are invalid");
    }
}
