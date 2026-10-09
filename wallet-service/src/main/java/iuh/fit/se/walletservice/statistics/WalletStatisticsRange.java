package iuh.fit.se.walletservice.statistics;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record WalletStatisticsRange(Instant dateFrom, Instant dateTo, String bucket) {
    public static final ZoneId TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static WalletStatisticsRange parse(String from, String to, String bucket) {
        Instant end = to == null || to.isBlank() ? LocalDate.now(TIME_ZONE).plusDays(1).atStartOfDay(TIME_ZONE).toInstant() : parseInstant(to);
        Instant start = from == null || from.isBlank() ? end.minusSeconds(30L * 86400) : parseInstant(from);
        if (!start.isBefore(end) || end.isAfter(start.atZone(TIME_ZONE).plusMonths(24).toInstant())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid statistics date range");
        String unit = bucket == null || bucket.isBlank() ? "DAY" : bucket.toUpperCase();
        if (!unit.equals("DAY") && !unit.equals("WEEK") && !unit.equals("MONTH")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bucket must be DAY, WEEK, or MONTH");
        return new WalletStatisticsRange(start, end, unit);
    }

    private static Instant parseInstant(String value) {
        try { return Instant.parse(value); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be ISO timestamps"); }
    }
}
