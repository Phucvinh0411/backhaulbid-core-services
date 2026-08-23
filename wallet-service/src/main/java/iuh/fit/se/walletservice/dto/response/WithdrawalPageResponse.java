package iuh.fit.se.walletservice.dto.response;

import java.util.List;

public record WithdrawalPageResponse(
        List<WithdrawalResponse> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
}
