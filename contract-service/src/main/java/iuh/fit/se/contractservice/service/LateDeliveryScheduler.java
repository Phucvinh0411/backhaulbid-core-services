package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class LateDeliveryScheduler {
    private final TripRepository tripRepository;
    private final LateDeliveryService lateDeliveryService;

    @Scheduled(fixedDelayString = "${LATE_DELIVERY_SCAN_INTERVAL_MS:60000}")
    public void reviewLateTrips() {
        tripRepository.findTripsForLateReview(Instant.now()).forEach(trip -> {
            try {
                lateDeliveryService.processTripAndReleaseIfClosed(trip);
            } catch (RuntimeException exception) {
                log.warn("Could not process late-delivery policy for trip {}: {}", trip.getId(), exception.getMessage());
            }
        });
    }
}
