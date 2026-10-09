package iuh.fit.se.service.driver;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Persistent, shared redeem rate limit (survives restarts, works across instances). Buckets are per client
 * IP and per IP + grant. There is deliberately no per-grant-only bucket: someone who only knows a grant ID
 * must not be able to lock that grant for its real driver.
 */
@Component
public class DriverRedeemThrottle {
    static final Duration WINDOW = Duration.ofMinutes(10);
    static final int PER_IP = 20;
    static final int PER_IP_AND_GRANT = 6;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DriverRedeemThrottle(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void hit(String clientKey, String grantId) {
        Instant now = clock.instant();
        if (count("ip:" + clientKey, now) > PER_IP || count("ip-grant:" + clientKey + ":" + grantId, now) > PER_IP_AND_GRANT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã thử quá nhiều lần. Vui lòng đợi vài phút rồi thử lại.");
        }
    }

    private int count(String bucket, Instant now) {
        Timestamp start = Timestamp.from(now), cutoff = Timestamp.from(now.minus(WINDOW));
        String key = bucket.length() > 100 ? bucket.substring(0, 100) : bucket;
        Integer attempts = jdbc.queryForObject("""
                INSERT INTO driver_redeem_throttle(bucket, window_start, attempts) VALUES (?, ?, 1)
                ON CONFLICT (bucket) DO UPDATE SET
                  attempts = CASE WHEN driver_redeem_throttle.window_start < ? THEN 1 ELSE driver_redeem_throttle.attempts + 1 END,
                  window_start = CASE WHEN driver_redeem_throttle.window_start < ? THEN EXCLUDED.window_start ELSE driver_redeem_throttle.window_start END
                RETURNING attempts
                """, Integer.class, key, start, cutoff, cutoff);
        return attempts == null ? 0 : attempts;
    }
}
