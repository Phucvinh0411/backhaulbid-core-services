package iuh.fit.se.walletservice.mapper;

import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import org.springframework.stereotype.Component;

/** Maps withdrawal requests while ensuring bank account numbers are masked. */
@Component
public class WithdrawalMapper {
    public WithdrawalResponse toResponse(WithdrawalRequest request) {
        return new WithdrawalResponse(
                request.getId(),
                request.getAccountId(),
                request.getAmount(),
                request.getBankName(),
                mask(request.getBankAccountNumber()),
                request.getAccountHolderName(),
                request.getStatus().name(),
                request.getProcessedBy(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getProcessedAt());
    }

    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return "****";
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
