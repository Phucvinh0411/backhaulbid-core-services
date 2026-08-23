package iuh.fit.se.walletservice.mapper;

import iuh.fit.se.walletservice.domain.operation.InternalWalletOperation;
import iuh.fit.se.walletservice.dto.response.InternalWalletOperationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Maps the domain operation result to the public internal API response. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InternalWalletOperationMapper {

    @Mapping(target = "status", expression = "java(operation.transaction().getStatus().name())")
    @Mapping(target = "transactionId", source = "transaction.id")
    @Mapping(target = "holdId", source = "holdId")
    @Mapping(target = "amount", source = "transaction.amount")
    @Mapping(target = "message", source = "transaction.description")
    InternalWalletOperationResponse toResponse(InternalWalletOperation operation);
}
