package iuh.fit.se.walletservice.dto.response;

import java.util.List;

public record PaymentOrderPageResponse(
        List<PaymentOrderResponse> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
}
