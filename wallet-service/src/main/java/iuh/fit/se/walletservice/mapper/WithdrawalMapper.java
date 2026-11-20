package iuh.fit.se.walletservice.mapper;

import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** Maps withdrawal requests while ensuring bank account numbers are masked. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WithdrawalMapper {

    @Mapping(target = "maskedBankAccountNumber", source = "bankAccountNumber", qualifiedByName = "maskBankAccount")
    WithdrawalResponse toResponse(WithdrawalRequest request);

    @Named("maskBankAccount")
    default String maskBankAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
