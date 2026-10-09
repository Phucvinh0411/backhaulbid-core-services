package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripHandover;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.ConfirmTripHandoverRequest;
import iuh.fit.se.contractservice.dto.RecordTripHandoverRequest;
import iuh.fit.se.contractservice.repository.TripHandoverRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.service.driveraccess.DriverAccessPolicy;
import iuh.fit.se.contractservice.service.driveraccess.DriverGrantService;
import iuh.fit.se.contractservice.service.driveraccess.UuidV7;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pickup handover: the driver (or carrier) records what was loaded and its condition before pickup, and the
 * shipper confirms the handover. The trip cannot leave WAITING_PICKUP without that confirmation; the check is
 * {@link #requireReadyForPickup} and is called by {@link TripService} on every path that starts a trip.
 *
 * <p>Photos are stored as object keys in the private handover-photos folder. They are read only through
 * {@link #photoKeyAt}, which checks the trip for the caller first, so no signed-in user can read a photo by URL.
 */
@Service
public class TripHandoverService {
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    /** A storage URL sent by an older client: only its handover-photos key is kept. */
    private static final Pattern PHOTO_URL = Pattern.compile(
            "^https?://[^\\s]{1,400}/(handover-photos/" + UUID_PATTERN + "\\.(png|jpe?g|webp|gif))$", Pattern.CASE_INSENSITIVE);
    /** The stored object key itself. */
    private static final Pattern PHOTO_KEY = Pattern.compile(
            "^(handover-photos/" + UUID_PATTERN + "\\.(png|jpe?g|webp|gif))$", Pattern.CASE_INSENSITIVE);
    /** A reference to a photo the record already has, by its position: the same path the responses return. */
    private static final String OWN_PHOTO_PATH = "^/api/v1/trips/%s/handover/photos/([0-9]{1,2})$";
    private static final int MAX_PHOTOS = 10;

    private final TripRepository trips;
    private final TripHandoverRepository handovers;
    private final DriverAccessPolicy driverAccessPolicy;
    private final DriverGrantService grants;
    private final Clock clock;
    private final UuidV7 ids;

    public TripHandoverService(TripRepository trips, TripHandoverRepository handovers, DriverAccessPolicy driverAccessPolicy,
                               DriverGrantService grants, Clock clock) {
        this.trips = trips;
        this.handovers = handovers;
        this.driverAccessPolicy = driverAccessPolicy;
        this.grants = grants;
        this.clock = clock;
        this.ids = new UuidV7(clock, new SecureRandom());
    }

    /**
     * Records or replaces the handover before pickup. A driver session may write only while its grant is current; a
     * carrier may write for its own trip. Once the shipper confirmed, the record is locked.
     */
    @Transactional
    public TripHandover record(UUID accountId, AccountRole role, UUID tripId, RecordTripHandoverRequest request) {
        Trip trip = lockTrip(tripId);
        String recordedByType = recorderType(trip, accountId, role);
        if (trip.getStatus() != TripStatus.WAITING_PICKUP) {
            throw conflict("Biên bản bàn giao chỉ ghi khi chuyến đang chờ lấy hàng");
        }
        TripHandover current = handovers.findByTripId(tripId).orElse(null);
        if (current != null && current.getShipperConfirmedAt() != null) {
            throw conflict("Chủ hàng đã xác nhận biên bản này, không sửa được nữa");
        }

        String condition = request.conditionStatus();
        String conditionNote = blankToNull(request.conditionNote());
        if (TripHandover.DAMAGED.equals(condition) && conditionNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi rõ tình trạng hư hỏng của hàng hóa");
        }
        List<String> photoKeys = normalizePhotos(request.photoUrls(), tripId, current);
        Instant now = clock.instant();

        TripHandover handover = current != null ? current : TripHandover.builder().id(ids.next()).tripId(tripId).build();
        handover.setCargoCategory(request.cargoCategory().trim());
        handover.setPackageCount(request.packageCount());
        handover.setGrossWeightKg(request.grossWeightKg().setScale(2));
        handover.setConditionStatus(condition);
        handover.setConditionNote(TripHandover.DAMAGED.equals(condition) ? conditionNote : null);
        handover.setSealNumber(blankToNull(request.sealNumber()));
        handover.setPlaceNote(blankToNull(request.placeNote()));
        handover.setRecordedBy(accountId);
        handover.setRecordedByType(recordedByType);
        handover.setRecordedAt(now);
        handover.setUpdatedAt(now);
        handover.getPhotoKeys().clear();
        handover.getPhotoKeys().addAll(photoKeys);
        try {
            return handovers.saveAndFlush(handover);
        } catch (DataIntegrityViolationException concurrentWrite) {
            throw conflict("Biên bản bàn giao vừa được ghi bởi một phiên khác. Tải lại và thử lại.");
        }
    }

    /** The shipper's confirmation that the goods were handed over. Requires an existing record with photos. */
    @Transactional
    public TripHandover confirm(UUID accountId, AccountRole role, UUID tripId, ConfirmTripHandoverRequest request) {
        if (role != AccountRole.SHIPPER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shipper can confirm the handover");
        }
        Trip trip = lockTrip(tripId);
        if (!accountId.equals(trip.getShipperId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shipper of this trip can confirm the handover");
        }
        if (trip.getStatus() != TripStatus.WAITING_PICKUP) {
            throw conflict("Chỉ xác nhận bàn giao khi chuyến đang chờ lấy hàng");
        }
        TripHandover handover = handovers.findByTripId(tripId)
                .orElseThrow(() -> conflict("Tài xế chưa ghi biên bản bàn giao"));
        if (handover.getShipperConfirmedAt() != null) {
            throw conflict("Biên bản này đã được xác nhận");
        }
        if (handover.getPhotoKeys().isEmpty()) {
            throw conflict("Biên bản chưa có ảnh hàng hóa");
        }
        Instant now = clock.instant();
        handover.setShipperSignerName(request.signerName().trim());
        handover.setShipperConfirmedBy(accountId);
        handover.setShipperConfirmedAt(now);
        handover.setUpdatedAt(now);
        return handovers.saveAndFlush(handover);
    }

    /** The trip's handover (zero or one). Visible to its shipper, its carrier, a current driver session and admins. */
    @Transactional(readOnly = true)
    public List<TripHandover> view(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        requireReadAccess(accountId, role, trip);
        return handovers.findByTripId(tripId).map(List::of).orElse(List.of());
    }

    /**
     * The object key of one handover photo, after the caller passed the same trip check as for the record. Only the
     * contract service reads the bytes, and only with this key.
     */
    @Transactional(readOnly = true)
    public String photoKeyAt(UUID accountId, AccountRole role, UUID tripId, int index) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        requireReadAccess(accountId, role, trip);
        TripHandover handover = handovers.findByTripId(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa có biên bản bàn giao"));
        List<String> keys = handover.getPhotoKeys();
        if (index < 0 || index >= keys.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có ảnh bàn giao này");
        }
        return keys.get(index);
    }

    /** Pickup gate used by {@link TripService}: the trip may start only with a photographed, confirmed handover. */
    @Transactional(readOnly = true)
    public void requireReadyForPickup(UUID tripId) {
        boolean ready = handovers.findByTripId(tripId).map(TripHandover::isReadyForPickup).orElse(false);
        if (!ready) {
            throw conflict("Cần biên bản bàn giao có ảnh và xác nhận của chủ hàng trước khi lấy hàng");
        }
    }

    /**
     * Whether a driver session may read a handover photo of its own trip. Called from the internal media check; the
     * session must be readable and the key must be one of that trip's handover photos.
     */
    @Transactional(readOnly = true)
    public boolean canReadPhoto(UUID sessionId, String objectKey) {
        if (objectKey == null || !PHOTO_KEY.matcher(objectKey).matches()) return false;
        DriverGrantService.SessionStatus status;
        try {
            status = grants.status(sessionId);
        } catch (ResponseStatusException missing) {
            return false;
        }
        if (!status.readable()) return false;
        return handovers.findByTripId(status.tripId())
                .map(handover -> handover.getPhotoKeys().contains(objectKey))
                .orElse(false);
    }

    private void requireReadAccess(UUID accountId, AccountRole role, Trip trip) {
        boolean allowed;
        if (role == AccountRole.ADMIN) {
            allowed = true;
        } else if (role == AccountRole.CARRIER) {
            allowed = accountId.equals(trip.getCarrierId());
        } else if (role == AccountRole.SHIPPER) {
            allowed = accountId.equals(trip.getShipperId());
        } else if (role == AccountRole.DRIVER) {
            allowed = driverAccessPolicy.allows(trip, accountId, role, DriverAccessPolicy.Mode.READ);
        } else {
            allowed = false;
        }
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this trip");
        }
    }

    private Trip lockTrip(UUID tripId) {
        return trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    /** Who is recording: the driver session (only while its grant is current) or the trip's carrier. */
    private String recorderType(Trip trip, UUID accountId, AccountRole role) {
        if (role == AccountRole.DRIVER) {
            if (!driverAccessPolicy.allows(trip, accountId, role, DriverAccessPolicy.Mode.WRITE)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver session is not current for this trip");
            }
            return TripHandover.RECORDED_BY_DRIVER;
        }
        if (role == AccountRole.CARRIER && accountId.equals(trip.getCarrierId())) {
            return TripHandover.RECORDED_BY_CARRIER;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip's driver or carrier can record the handover");
    }

    /**
     * Turns the request's photo references into object keys. Accepted: a stored key, a storage URL from the
     * handover-photos folder (its key is kept), or a path of this trip's photo as the record already holds it.
     */
    static List<String> normalizePhotos(List<String> raw, UUID tripId, TripHandover current) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thêm từ 1 đến " + MAX_PHOTOS + " ảnh hàng hóa");
        }
        Pattern ownPath = Pattern.compile(String.format(OWN_PHOTO_PATH, tripId));
        List<String> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : raw) {
            String key = keyFor(value == null ? "" : value.trim(), ownPath, current);
            if (seen.add(key.toLowerCase(Locale.ROOT))) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thêm từ 1 đến " + MAX_PHOTOS + " ảnh hàng hóa");
        }
        return keys;
    }

    private static String keyFor(String text, Pattern ownPath, TripHandover current) {
        Matcher reference = ownPath.matcher(text);
        if (reference.matches()) {
            int index = Integer.parseInt(reference.group(1));
            if (current == null || index >= current.getPhotoKeys().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh đã chọn không còn trong biên bản");
            }
            return current.getPhotoKeys().get(index);
        }
        Matcher key = PHOTO_KEY.matcher(text);
        if (key.matches()) return key.group(1);
        Matcher url = PHOTO_URL.matcher(text);
        if (url.matches()) return url.group(1);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh hàng hóa không hợp lệ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
