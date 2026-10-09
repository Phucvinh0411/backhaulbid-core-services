package iuh.fit.se.contractservice.statistics;

import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripStatisticsService {
    private final EntityManager entityManager;

    public Map<String, Object> owner(UUID accountId, String role, StatisticsRange range) {
        String column = switch (role) {
            case "SHIPPER" -> "shipper_id";
            case "CARRIER" -> "carrier_id";
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Statistics are available to shippers and carriers");
        };
        return aggregate(column, accountId, range);
    }

    public Map<String, Object> admin(StatisticsRange range) {
        return aggregate(null, null, range);
    }

    private Map<String, Object> aggregate(String ownerColumn, UUID accountId, StatisticsRange range) {
        String ownerPredicate = ownerColumn == null ? "" : " AND " + ownerColumn + " = :accountId";
        String tripFilter = "created_at >= :dateFrom AND created_at < :dateTo" + ownerPredicate;
        long total = number(query("SELECT COUNT(*) FROM trips WHERE " + tripFilter, range, ownerColumn, accountId).getSingleResult());

        Map<String, Long> byStatus = zeros(TripStatus.values());
        for (Object[] row : rows("SELECT status, COUNT(*) FROM trips WHERE " + tripFilter + " GROUP BY status", range, ownerColumn, accountId)) {
            byStatus.put(String.valueOf(row[0]), number(row[1]));
        }

        Map<String, Long> contractsByStatus = zeros(ContractStatus.values());
        String contractFilter = "t.created_at >= :dateFrom AND t.created_at < :dateTo" + (ownerColumn == null ? "" : " AND c." + ownerColumn + " = :accountId");
        String contractBinding = ownerColumn == null ? null : "contract";
        for (Object[] row : rows("SELECT c.status, COUNT(*) FROM contracts c JOIN trips t ON t.id = c.trip_id WHERE " + contractFilter + " GROUP BY c.status", range, contractBinding, accountId)) {
            contractsByStatus.put(String.valueOf(row[0]), number(row[1]));
        }

        String valueFilter = contractFilter + " AND c.status = 'SIGNED'";
        Object value = query("SELECT COALESCE(SUM(t.agreed_price), 0) FROM contracts c JOIN trips t ON t.id = c.trip_id WHERE " + valueFilter, range, contractBinding, accountId).getSingleResult();

        String truncated = switch (range.bucket()) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };
        String timeSeriesSql = "SELECT date_trunc('" + truncated + "', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, COUNT(*) FROM trips WHERE " + tripFilter + " GROUP BY bucket ORDER BY bucket";
        Map<String, Long> observed = new LinkedHashMap<>();
        for (Object[] row : rows(timeSeriesSql, range, ownerColumn, accountId)) {
            LocalDateTime bucket = row[0] instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) row[0];
            observed.put(bucket.toLocalDate().toString(), number(row[1]));
        }

        List<Map<String, Object>> series = new ArrayList<>();
        LocalDateTime current = range.dateFrom().atZone(StatisticsRange.TIME_ZONE).toLocalDate().atStartOfDay();
        if (range.bucket() == StatisticsRange.Bucket.WEEK) current = current.minusDays(current.getDayOfWeek().getValue() - 1L);
        if (range.bucket() == StatisticsRange.Bucket.MONTH) current = current.withDayOfMonth(1);
        LocalDateTime last = range.dateTo().minusNanos(1).atZone(StatisticsRange.TIME_ZONE).toLocalDate().atStartOfDay();
        if (range.bucket() == StatisticsRange.Bucket.WEEK) last = last.minusDays(last.getDayOfWeek().getValue() - 1L);
        if (range.bucket() == StatisticsRange.Bucket.MONTH) last = last.withDayOfMonth(1);
        while (!current.isAfter(last)) {
            String date = current.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            series.add(Map.of("date", date, "count", observed.getOrDefault(date, 0L)));
            current = switch (range.bucket()) {
                case DAY -> current.plusDays(1);
                case WEEK -> current.plusWeeks(1);
                case MONTH -> current.plusMonths(1);
            };
        }

        return Map.of(
                "dateFrom", range.dateFrom().toString(),
                "dateTo", range.dateTo().toString(),
                "bucket", range.bucket().name(),
                "total", total,
                "byStatus", byStatus,
                "contractsByStatus", contractsByStatus,
                "agreedValue", value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString()),
                "series", series,
                "refreshedAt", java.time.Instant.now().toString()
        );
    }

    private Query query(String sql, StatisticsRange range, String ownerColumn, UUID accountId) {
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("dateFrom", range.dateFrom())
                .setParameter("dateTo", range.dateTo());
        if (ownerColumn != null) query.setParameter("accountId", accountId);
        return query;
    }

    private List<Object[]> rows(String sql, StatisticsRange range, String ownerColumn, UUID accountId) {
        return query(sql, range, ownerColumn, accountId).getResultList();
    }

    private static long number(Object value) { return value == null ? 0L : ((Number) value).longValue(); }

    private static <E extends Enum<E>> Map<String, Long> zeros(E[] values) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (E value : values) result.put(value.name(), 0L);
        return result;
    }
}
