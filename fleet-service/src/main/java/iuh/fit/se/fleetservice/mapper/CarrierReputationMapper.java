package iuh.fit.se.fleetservice.mapper;

import iuh.fit.se.fleetservice.domain.entity.CarrierReputation;
import iuh.fit.se.fleetservice.domain.entity.CarrierReputationEntry;
import iuh.fit.se.fleetservice.dto.CarrierReputationEntryResponse;
import iuh.fit.se.fleetservice.dto.CarrierReputationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CarrierReputationMapper {

    CarrierReputationResponse toResponse(CarrierReputation reputation);

    CarrierReputationEntryResponse toEntryResponse(CarrierReputationEntry entry);

    List<CarrierReputationEntryResponse> toEntryResponses(List<CarrierReputationEntry> entries);
}
