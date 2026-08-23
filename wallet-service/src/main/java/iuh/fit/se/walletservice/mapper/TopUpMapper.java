package iuh.fit.se.walletservice.mapper;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import iuh.fit.se.walletservice.dto.response.TopUpResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.Map;

/** Maps a persisted payment order and provider form fields to the API response. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TopUpMapper {
    @Mapping(target = "paymentOrderId", source = "paymentOrder.id")
    @Mapping(target = "invoiceNumber", source = "paymentOrder.invoiceNumber")
    @Mapping(target = "amount", source = "paymentOrder.amount")
    @Mapping(target = "status", expression = "java(paymentOrder.getStatus().name())")
    @Mapping(target = "checkoutUrl", source = "paymentOrder.checkoutUrl")
    @Mapping(target = "formFields", source = "formFields")
    @Mapping(target = "expiresAt", source = "paymentOrder.expiresAt")
    TopUpResponse toResponse(PaymentOrder paymentOrder, Map<String, String> formFields);
}
