package iuh.fit.se.contractservice.mapper;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.ContractSignature;
import iuh.fit.se.contractservice.dto.ContractResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = TripMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ContractMapper {

    @Mapping(target = "tripId", source = "trip.id")
    @Mapping(target = "signatures", expression = "java(toSignatureResponses(contract.getSignatures()))")
    @Mapping(target = "shipperName", ignore = true)
    @Mapping(target = "carrierName", ignore = true)
    ContractResponse toResponse(Contract contract);

    List<ContractResponse> toResponses(List<Contract> contracts);

    ContractResponse.SignatureResponse toSignatureResponse(ContractSignature signature);

    default List<ContractResponse.SignatureResponse> toSignatureResponses(List<ContractSignature> signatures) {
        if (signatures == null || signatures.isEmpty()) {
            return List.of();
        }
        return signatures.stream().map(this::toSignatureResponse).toList();
    }
}
