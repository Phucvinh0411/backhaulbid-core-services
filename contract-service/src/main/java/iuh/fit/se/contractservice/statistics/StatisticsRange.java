package iuh.fit.se.contractservice.statistics;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record StatisticsRange(Instant dateFrom, Instant dateTo, Bucket bucket) {
    public static final ZoneId TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public enum Bucket { DAY, WEEK, MONTH }

    public static StatisticsRange parse(String from, String to, String bucketValue) {
        Instant end = to == null || to.isBlank()
                ? LocalDate.now(TIME_ZONE).plusDays(1).atStartOfDay(TIME_ZONE).toInstant()
                : parseInstant(to);
        Instant start = from == null || from.isBlank() ? end.minusSeconds(30L * 24 * 60 * 60) : parseInstant(from);
        if (!start.isBefore(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must be before dateTo");
        if (end.isAfter(start.atZone(TIME_ZONE).plusMonths(24).toInstant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statistics range cannot exceed 24 months");
        }
        Bucket bucket;
        try { bucket = Bucket.valueOf(bucketValue == null || bucketValue.isBlank() ? "DAY" : bucketValue.toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bucket must be DAY, WEEK, or MONTH"); }
        return new StatisticsRange(start, end, bucket);
    }

    private static Instant parseInstant(String value) {
        try { return Instant.parse(value); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be ISO timestamps"); }
    }
}
