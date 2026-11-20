package iuh.fit.se.fleetservice.mapper;

import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import iuh.fit.se.fleetservice.dto.DriverResponse;
import iuh.fit.se.fleetservice.dto.PublicDriverResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DriverMapper {

    DriverResponse toResponse(DriverProfile driver);

    List<DriverResponse> toResponses(List<DriverProfile> drivers);

    PublicDriverResponse toPublicResponse(DriverProfile driver);

    List<PublicDriverResponse> toPublicResponses(List<DriverProfile> drivers);
}
