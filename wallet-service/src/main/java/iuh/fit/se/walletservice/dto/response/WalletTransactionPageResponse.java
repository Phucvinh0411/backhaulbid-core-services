package iuh.fit.se.walletservice.dto.response;

import java.util.List;

public record WalletTransactionPageResponse(
        List<WalletTransactionResponse> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
}
