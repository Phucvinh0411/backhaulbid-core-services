package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component @RequiredArgsConstructor
public class TripGpsCleanup {
    private static final long STOP_INVALID_LOCK_KEY = 4_200_002L;
    private static final long PURGE_GPS_LOCK_KEY = 4_200_003L;

    private final TripTrackingSessionRepository sessions;
    private final TripLocationUpdateRepository points;
    private final Clock clock;
    private final PostgresAdvisoryLock advisoryLock;

    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void stopInvalidSessions() {
        advisoryLock.runInTransactionWithLock(STOP_INVALID_LOCK_KEY, () -> sessions.stopInvalid(clock.instant()));
    }

    @Scheduled(fixedDelay=3600000,initialDelay=3600000)
    public void purgeExpiredGps() {
        advisoryLock.runInTransactionWithLock(
                PURGE_GPS_LOCK_KEY,
                () -> points.deleteExpiredGps(clock.instant().minus(Duration.ofDays(30))));
    }
}
