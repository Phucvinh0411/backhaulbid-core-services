package iuh.fit.se.walletservice.dto.request;

import jakarta.validation.constraints.Size;

public record WithdrawalDecisionRequest(
        @Size(max = 500)
        String reason
) {
}
