package iuh.fit.se.mapper;

import iuh.fit.se.domain.dto.response.AuthResponse;
import iuh.fit.se.domain.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "phone", source = "account.phone")
    @Mapping(target = "role", source = "account.role")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    AuthResponse toResponse(
            Account account,
            String accessToken,
            String refreshToken);
}
