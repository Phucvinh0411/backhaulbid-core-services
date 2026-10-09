package iuh.fit.se.walletservice.statistics;

import iuh.fit.se.walletservice.domain.enums.TransactionStatus;
import iuh.fit.se.walletservice.domain.enums.TransactionType;
import iuh.fit.se.walletservice.repository.TransactionRepository;
import iuh.fit.se.walletservice.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WalletStatisticsService {
    private final TransactionRepository transactions;
    private final WithdrawalRequestRepository withdrawals;

    public Map<String, Object> admin(String role, WalletStatisticsRange range) {
        if (!"ADMIN".equalsIgnoreCase(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required");
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TransactionStatus status : TransactionStatus.values()) byStatus.put(status.name(), 0L);
        for (Object[] row : transactions.aggregateStatuses(range.dateFrom(), range.dateTo())) byStatus.put(String.valueOf(row[0]), ((Number) row[1]).longValue());

        Map<String, Map<String, Object>> successfulByType = new LinkedHashMap<>();
        BigDecimal successfulAmount = BigDecimal.ZERO;
        for (TransactionType type : TransactionType.values()) successfulByType.put(type.name(), Map.of("count", 0L, "amount", BigDecimal.ZERO));
        for (Object[] row : transactions.aggregateSuccessfulByType(TransactionStatus.SUCCESS, range.dateFrom(), range.dateTo())) {
            BigDecimal amount = row[2] instanceof BigDecimal value ? value : new BigDecimal(row[2].toString());
            successfulByType.put(String.valueOf(row[0]), Map.of("count", ((Number) row[1]).longValue(), "amount", amount));
            successfulAmount = successfulAmount.add(amount);
        }

        Map<String, Object[]> observed = new LinkedHashMap<>();
        for (Object[] row : transactions.aggregateSuccessfulSeries(range.bucket().toLowerCase(), range.dateFrom(), range.dateTo())) {
            LocalDateTime bucket = row[0] instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) row[0];
            observed.put(bucket.toLocalDate().toString(), row);
        }
        List<Map<String, Object>> series = zeroFill(range, observed);
        Map<String, Long> withdrawalsByStatus = new LinkedHashMap<>();
        for (var status : iuh.fit.se.walletservice.domain.enums.WithdrawalStatus.values()) withdrawalsByStatus.put(status.name(), withdrawals.countByStatus(status));
        return Map.of("dateFrom", range.dateFrom().toString(), "dateTo", range.dateTo().toString(), "bucket", range.bucket(),
                "transactionsByStatus", byStatus, "successfulByType", successfulByType, "successfulLedgerAmount", successfulAmount,
                "successfulSeries", series, "withdrawalsByStatus", withdrawalsByStatus,
                "refreshedAt", java.time.Instant.now().toString());
    }

    private static List<Map<String, Object>> zeroFill(WalletStatisticsRange range, Map<String, Object[]> observed) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime current = range.dateFrom().atZone(WalletStatisticsRange.TIME_ZONE).toLocalDate().atStartOfDay();
        if (range.bucket().equals("WEEK")) current = current.minusDays(current.getDayOfWeek().getValue() - 1L);
        if (range.bucket().equals("MONTH")) current = current.withDayOfMonth(1);
        LocalDateTime last = range.dateTo().minusNanos(1).atZone(WalletStatisticsRange.TIME_ZONE).toLocalDate().atStartOfDay();
        if (range.bucket().equals("WEEK")) last = last.minusDays(last.getDayOfWeek().getValue() - 1L);
        if (range.bucket().equals("MONTH")) last = last.withDayOfMonth(1);
        while (!current.isAfter(last)) {
            String key = current.toLocalDate().toString();
            Object[] row = observed.get(key);
            result.add(Map.of("date", key, "count", row == null ? 0L : ((Number) row[1]).longValue(), "amount", row == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString())));
            current = switch (range.bucket()) { case "DAY" -> current.plusDays(1); case "WEEK" -> current.plusWeeks(1); default -> current.plusMonths(1); };
        }
        return result;
    }
}
