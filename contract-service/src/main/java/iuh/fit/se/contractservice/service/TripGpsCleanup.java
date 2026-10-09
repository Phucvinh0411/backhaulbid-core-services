package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Component @RequiredArgsConstructor
public class TripGpsCleanup {
    private final TripTrackingSessionRepository sessions;
    private final TripLocationUpdateRepository points;
    private final Clock clock;
    @Scheduled(fixedDelay=60000,initialDelay=60000) @Transactional
    public void stopInvalidSessions(){sessions.stopInvalid(clock.instant());}
    @Scheduled(fixedDelay=3600000,initialDelay=3600000) @Transactional
    public void purgeExpiredGps(){points.deleteExpiredGps(clock.instant().minus(Duration.ofDays(30)));}
}
