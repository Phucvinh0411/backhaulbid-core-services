package iuh.fit.se.walletservice.mapper;

import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.dto.response.WalletResponse;
import iuh.fit.se.walletservice.dto.response.WalletTransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Keeps transport DTO construction out of controllers and services. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WalletMapper {
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "balance", source = "balance")
    @Mapping(target = "frozenBalance", source = "frozenBalance")
    @Mapping(target = "availableBalance", expression = "java(wallet.getBalance().subtract(wallet.getFrozenBalance()))")
    WalletResponse toResponse(Wallet wallet);

    @Mapping(target = "type", expression = "java(transaction.getType().name())")
    @Mapping(target = "status", expression = "java(transaction.getStatus().name())")
    @Mapping(target = "paymentMethod", expression = "java(transaction.getPaymentMethod() == null ? null : transaction.getPaymentMethod().name())")
    WalletTransactionResponse toTransactionResponse(Transaction transaction);
}
