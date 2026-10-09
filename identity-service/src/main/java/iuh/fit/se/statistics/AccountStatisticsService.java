package iuh.fit.se.statistics;

import iuh.fit.se.domain.enums.AccountRole;
import iuh.fit.se.domain.enums.AccountStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountStatisticsService {
    private final EntityManager entityManager;

    public Map<String, Object> admin(AccountStatisticsRange range) {
        Map<String, Long> currentByRole = zeros(AccountRole.values());
        Map<String, Long> currentByStatus = zeros(AccountStatus.values());
        for (Object item : entityManager.createNativeQuery("SELECT role, status, COUNT(*) FROM accounts GROUP BY role, status").getResultList()) {
            Object[] row = (Object[]) item;
            long count = ((Number) row[2]).longValue();
            currentByRole.merge(String.valueOf(row[0]), count, Long::sum);
            currentByStatus.merge(String.valueOf(row[1]), count, Long::sum);
        }

        List<?> newAccounts = query("SELECT role, status, COUNT(*) FROM accounts WHERE created_at >= :dateFrom AND created_at < :dateTo GROUP BY role, status", range).getResultList();
        Map<String, Long> newByRole = zeros(AccountRole.values());
        Map<String, Long> newByStatus = zeros(AccountStatus.values());
        long totalNew = 0;
        for (Object item : newAccounts) {
            Object[] row = (Object[]) item;
            long count = ((Number) row[2]).longValue();
            newByRole.merge(String.valueOf(row[0]), count, Long::sum);
            newByStatus.merge(String.valueOf(row[1]), count, Long::sum);
            totalNew += count;
        }

        String unit = range.bucket().toLowerCase();
        String sql = "SELECT date_trunc('" + unit + "', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, COUNT(*) FROM accounts WHERE created_at >= :dateFrom AND created_at < :dateTo GROUP BY bucket ORDER BY bucket";
        Map<String, Long> observed = new LinkedHashMap<>();
        for (Object item : query(sql, range).getResultList()) {
            Object[] row = (Object[]) item;
            LocalDateTime date = row[0] instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) row[0];
            observed.put(date.toLocalDate().toString(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> series = zeroFill(range, observed);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dateFrom", range.dateFrom().toString());
        response.put("dateTo", range.dateTo().toString());
        response.put("bucket", range.bucket());
        response.put("totalNew", totalNew);
        response.put("newByRole", newByRole);
        response.put("newByStatus", newByStatus);
        response.put("currentByRole", currentByRole);
        response.put("currentByStatus", currentByStatus);
        response.put("currentVerificationQueues", verificationQueues());
        response.put("series", series);
        response.put("refreshedAt", java.time.Instant.now().toString());
        return response;
    }

    private Map<String, Long> verificationQueues() {
        return Map.of("businessPending", nativeCount("SELECT COUNT(*) FROM companies WHERE verification_status = 'PENDING'"),
                "representativePending", nativeCount("SELECT COUNT(*) FROM ekyc_verifications WHERE status = 'PENDING'"));
    }

    private long nativeCount(String sql) { return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue(); }

    private Query query(String sql, AccountStatisticsRange range) {
        return entityManager.createNativeQuery(sql).setParameter("dateFrom", range.dateFrom()).setParameter("dateTo", range.dateTo());
    }

    private static List<Map<String, Object>> zeroFill(AccountStatisticsRange range, Map<String, Long> observed) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime current = range.dateFrom().atZone(AccountStatisticsRange.TIME_ZONE).toLocalDate().atStartOfDay();
        if (range.bucket().equals("WEEK")) current = current.minusDays(current.getDayOfWeek().getValue() - 1L);
        if (range.bucket().equals("MONTH")) current = current.withDayOfMonth(1);
        LocalDateTime last = range.dateTo().minusNanos(1).atZone(AccountStatisticsRange.TIME_ZONE).toLocalDate().atStartOfDay();
        if (range.bucket().equals("WEEK")) last = last.minusDays(last.getDayOfWeek().getValue() - 1L);
        if (range.bucket().equals("MONTH")) last = last.withDayOfMonth(1);
        while (!current.isAfter(last)) {
            String key = current.toLocalDate().toString();
            result.add(Map.of("date", key, "count", observed.getOrDefault(key, 0L)));
            current = switch (range.bucket()) { case "DAY" -> current.plusDays(1); case "WEEK" -> current.plusWeeks(1); default -> current.plusMonths(1); };
        }
        return result;
    }

    private static <E extends Enum<E>> Map<String, Long> zeros(E[] values) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (E value : values) result.put(value.name(), 0L);
        return result;
    }
}
