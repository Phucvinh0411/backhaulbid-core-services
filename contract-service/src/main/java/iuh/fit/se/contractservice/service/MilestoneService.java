package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripLocationUpdate;
import iuh.fit.se.contractservice.domain.entity.TripLocationSource;
import iuh.fit.se.contractservice.repository.TripLocationUpdateRepository;
import iuh.fit.se.contractservice.domain.entity.TripMilestone;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.MilestoneStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CreateMilestoneRequest;
import iuh.fit.se.contractservice.dto.MilestoneCheckInRequest;
import iuh.fit.se.contractservice.repository.TripMilestoneRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.service.driveraccess.DriverAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneService {

    /** Bán kính geo-fence cho phép tài xế check-in (km). */
    private static final double GEO_FENCE_RADIUS_KM = 2.0;

    private static final String EXCHANGE    = "contract.events";
    private static final String ROUTING_KEY = "trip.milestone.reached";

    private final TripRepository          tripRepository;
    private final TripMilestoneRepository milestoneRepository;
    private final GeoFencingService       geoFencingService;
    private final RabbitTemplate          rabbitTemplate;
    private final TripLocationUpdateRepository locationRepository;
    private final DriverAccessPolicy driverAccess;

    // ------------------------------------------------------------------ QUERY

    @Transactional(readOnly = true)
    public List<TripMilestone> listMilestones(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = getTrip(tripId);
        ensureAccess(trip, accountId, role);
        return milestoneRepository.findByTripIdOrderBySequenceOrderAsc(tripId);
    }

    // ----------------------------------------------------------------- CREATE

    @Transactional
    public TripMilestone createMilestone(UUID accountId, AccountRole role, UUID tripId,
                                         CreateMilestoneRequest request) {
        if (role != AccountRole.CARRIER && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
        Trip trip = getTrip(tripId);
        ensureAccess(trip, accountId, role);

        TripMilestone milestone = TripMilestone.builder()
                .trip(trip)
                .milestoneName(request.milestoneName().trim())
                .targetLat(request.targetLat())
                .targetLng(request.targetLng())
                .sequenceOrder(request.sequenceOrder())
                .status(MilestoneStatus.PENDING)
                .build();

        return milestoneRepository.save(milestone);
    }

    // ----------------------------------------------------------------- CHECKIN

    /**
     * Xử lý Check-in của tài xế tại một cột mốc.
     *
     * <ol>
     *   <li>Xác thực quyền truy cập (DRIVER hoặc ADMIN)</li>
     *   <li>Kiểm tra trạng thái chuyến — phải đang IN_TRANSIT</li>
     *   <li>Kiểm tra milestone chưa được check-in</li>
     *   <li>⭐ Geo-fencing bằng công thức Haversine — khoảng cách ≤ 2 km</li>
     *   <li>Cập nhật milestone → REACHED</li>
     *   <li>Publish event lên RabbitMQ</li>
     * </ol>
     */
    @Transactional
    public TripMilestone checkIn(UUID accountId, AccountRole role, UUID tripId,
                                 UUID milestoneId, MilestoneCheckInRequest request) {
        if (role != AccountRole.DRIVER && role != AccountRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver or admin role required");
        }

        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        ensureAccess(trip, accountId, role, DriverAccessPolicy.Mode.WRITE);

        // Chuyến phải đang vận chuyển mới được check-in milestone
        if (trip.getStatus() != TripStatus.IN_TRANSIT && trip.getStatus() != TripStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Trip must be in PICKED_UP or IN_TRANSIT status to check-in milestones");
        }

        TripMilestone milestone = milestoneRepository
                .findByIdAndTripId(milestoneId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Milestone not found for this trip"));

        // Không cho check-in lại
        if (milestone.getStatus() == MilestoneStatus.REACHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Milestone has already been reached");
        }

        // ⭐ GEO-FENCING — tính khoảng cách bằng Haversine
        double distanceKm = geoFencingService.calculateDistanceKm(
                request.currentLat(), request.currentLng(),
                milestone.getTargetLat(), milestone.getTargetLng()
        );

        log.info("[Milestone CheckIn] tripId={} milestoneId={} | distance={} km | limit={} km",
                tripId, milestoneId, String.format("%.3f", distanceKm), GEO_FENCE_RADIUS_KM);

        if (distanceKm > GEO_FENCE_RADIUS_KM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format(
                            "Vị trí không hợp lệ. Bạn đang ở quá xa cột mốc! " +
                            "(Khoảng cách hiện tại: %.2f km, cho phép: %.1f km)",
                            distanceKm, GEO_FENCE_RADIUS_KM));
        }

        // Cập nhật milestone
        milestone.setStatus(MilestoneStatus.REACHED);
        milestone.setActualLat(request.currentLat());
        milestone.setActualLng(request.currentLng());
        milestone.setReachedAt(Instant.now());
        TripMilestone saved = milestoneRepository.save(milestone);
        // The check-in is a real coordinate observation, not an inferred position.
        locationRepository.save(TripLocationUpdate.builder()
                .trip(trip).actorId(accountId).actorRole(role).milestoneId(saved.getId())
                .actorType(role == AccountRole.DRIVER && DriverAccessPolicy.isDriverSession() ? "DRIVER_SESSION" : "ACCOUNT").latitude(request.currentLat()).longitude(request.currentLng())
                .capturedAt(saved.getReachedAt()).label(saved.getMilestoneName())
                .source(TripLocationSource.CHECK_IN).status(trip.getStatus()).build());

        // Publish RabbitMQ event
        publishMilestoneReachedEvent(saved, trip);

        return saved;
    }

    // ----------------------------------------------------------- PRIVATE HELPERS

    private Trip getTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    private void ensureAccess(Trip trip, UUID accountId, AccountRole role) {
        ensureAccess(trip, accountId, role, DriverAccessPolicy.Mode.READ);
    }

    private void ensureAccess(Trip trip, UUID accountId, AccountRole role, DriverAccessPolicy.Mode mode) {
        boolean allowed = role == AccountRole.ADMIN
                || (role == AccountRole.CARRIER && accountId.equals(trip.getCarrierId()))
                || driverAccess.allows(trip, accountId, role, mode)
                || (role == AccountRole.SHIPPER && accountId.equals(trip.getShipperId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }
    }

    private void publishMilestoneReachedEvent(TripMilestone milestone, Trip trip) {
        try {
            Map<String, Object> event = Map.of(
                    "milestoneId",   milestone.getId().toString(),
                    "tripId",        trip.getId().toString(),
                    "milestoneName", milestone.getMilestoneName(),
                    "actualLat",     milestone.getActualLat(),
                    "actualLng",     milestone.getActualLng(),
                    "reachedAt",     milestone.getReachedAt().toString(),
                    "shipperId",     trip.getShipperId().toString(),
                    "carrierId",     trip.getCarrierId().toString()
            );
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
            log.info("[Milestone CheckIn] ✅ Published event '{}' for milestone {}", ROUTING_KEY, milestone.getId());
        } catch (Exception ex) {
            // Không rollback transaction chỉ vì MQ lỗi
            log.error("[Milestone CheckIn] ⚠️ Failed to publish RabbitMQ event: {}", ex.getMessage());
        }
    }
}
