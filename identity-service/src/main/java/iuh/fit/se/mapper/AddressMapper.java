package iuh.fit.se.mapper;

import iuh.fit.se.domain.dto.response.AddressResponse;
import iuh.fit.se.domain.entity.AddressBookEntry;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AddressMapper {
    AddressResponse toResponse(AddressBookEntry entry);

    List<AddressResponse> toResponses(List<AddressBookEntry> entries);
}
