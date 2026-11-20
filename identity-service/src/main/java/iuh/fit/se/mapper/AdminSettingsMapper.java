package iuh.fit.se.mapper;

import iuh.fit.se.domain.dto.response.AdminSettingsResponse;
import iuh.fit.se.domain.entity.AdminSetting;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.Map;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdminSettingsMapper {

    @Mapping(target = "scope", source = "setting.scope")
    @Mapping(target = "values", source = "values")
    @Mapping(target = "updatedAt", source = "setting.updatedAt")
    AdminSettingsResponse toResponse(AdminSetting setting, Map<String, Object> values);
}
