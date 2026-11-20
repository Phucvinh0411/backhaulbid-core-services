package iuh.fit.se.fleetservice.mapper;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.dto.EmptyRouteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EmptyRouteMapper {

    EmptyRouteResponse toResponse(EmptyRoute entity);

    List<EmptyRouteResponse> toResponses(List<EmptyRoute> entities);
}
