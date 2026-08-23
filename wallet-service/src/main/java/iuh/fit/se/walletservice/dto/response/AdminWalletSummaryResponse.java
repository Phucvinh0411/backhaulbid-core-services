package iuh.fit.se.walletservice.dto.response;

import java.math.BigDecimal;

public record AdminWalletSummaryResponse(
        BigDecimal auctionFees,
        BigDecimal deposits,
        BigDecimal withdrawals,
        long successfulTransactions
) {}
