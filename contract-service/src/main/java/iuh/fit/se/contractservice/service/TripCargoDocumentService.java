package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripCargoDocument;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentKind;
import iuh.fit.se.contractservice.domain.enums.CargoDocumentSource;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.AddCargoDocumentRequest;
import iuh.fit.se.contractservice.dto.CargoDocumentResponse;
import iuh.fit.se.contractservice.dto.CargoDocumentsResponse;
import iuh.fit.se.contractservice.dto.CargoValueDocumentRef;
import iuh.fit.se.contractservice.repository.TripCargoDocumentRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.service.driveraccess.UuidV7;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The goods-value documents a trip holds: the copy taken at the award attempt, plus any the shipper added later. Only
 * the trip's shipper and admins read them. Carriers and drivers have no route here; a carrier reads them only inside a
 * shared claim pack. The object key never leaves this service: reads go through the private media proxy.
 */
@Service
public class TripCargoDocumentService {
    /** Snapshot rows plus shipper additions. Keeps one trip's list bounded. */
    public static final int MAX_DOCUMENTS = 20;

    private final TripRepository trips;
    private final TripCargoDocumentRepository documents;
    private final MediaEvidenceClient media;
    private final Clock clock;
    private final UuidV7 ids;

    public TripCargoDocumentService(TripRepository trips, TripCargoDocumentRepository documents,
                                    MediaEvidenceClient media, Clock clock) {
        this.trips = trips;
        this.documents = documents;
        this.media = media;
        this.clock = clock;
        this.ids = new UuidV7(clock, new SecureRandom());
    }

    /** One document's bytes, read from private storage after the caller is checked as the shipper or an admin. */
    public record CargoFile(byte[] bytes, String contentType) {
    }

    /** Writes the award attempt's documents once, when the trip is created. Callers have checked the snapshot. */
    @Transactional
    public void recordAwardSnapshot(Trip trip, List<CargoValueDocumentRef> refs) {
        if (refs.isEmpty()) return;
        documents.saveAll(refs.stream().map(ref -> TripCargoDocument.builder()
                .id(ids.next())
                .tripId(trip.getId())
                .documentId(ref.documentId())
                .kind(ref.kind())
                .source(CargoDocumentSource.AWARD_SNAPSHOT)
                .objectKey(ref.objectKey())
                .uploadedBy(ref.uploadedBy())
                .uploadedAt(ref.uploadedAt())
                .build()).toList());
    }

    /**
     * The trip's documents in the order they were added. Internal: the caller has already checked access to the trip
     * or to the claim that reads them.
     */
    @Transactional(readOnly = true)
    public List<TripCargoDocument> documentsOf(UUID tripId) {
        return documents.findByTripIdOrderByUploadedAtAscIdAsc(tripId);
    }

    @Transactional(readOnly = true)
    public CargoDocumentsResponse view(UUID accountId, AccountRole role, UUID tripId) {
        Trip trip = loadTrip(tripId);
        requireShipperOrAdmin(accountId, role, trip);
        List<TripCargoDocument> rows = documents.findByTripIdOrderByUploadedAtAscIdAsc(tripId);
        return new CargoDocumentsResponse(
                trip.getDeclaredGoodsValue(),
                trip.getCargoSnapshotHash() != null,
                rows.stream().anyMatch(row -> row.getKind().isValueProof()),
                rows.stream().map(CargoDocumentResponse::from).toList());
    }

    /**
     * Adds a file the shipper uploaded after the award. It is kept as its own row with who added it and when; the
     * award snapshot is never edited. Trips created before snapshots add their first documents this way.
     */
    @Transactional
    public CargoDocumentResponse addSupplemental(UUID accountId, AccountRole role, UUID tripId, AddCargoDocumentRequest request) {
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (role != AccountRole.SHIPPER || !accountId.equals(trip.getShipperId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ chủ hàng của chuyến mới bổ sung được chứng từ");
        }
        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chuyến đã hủy, không bổ sung chứng từ");
        }
        if (request.kind() == CargoDocumentKind.UNCLASSIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chọn loại chứng từ: hóa đơn, biên nhận, hợp đồng, chứng từ thanh toán hoặc khác");
        }
        String key = request.key().trim();
        String documentId = CargoObjectKeys.documentIdOf(key);
        if (documentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp chứng từ không hợp lệ. Hãy tải lại tệp PDF hoặc ảnh.");
        }
        if (documents.existsByTripIdAndObjectKey(tripId, key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chứng từ này đã có trong chuyến");
        }
        if (documents.countByTripId(tripId) >= MAX_DOCUMENTS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chuyến đã đủ số chứng từ tối đa");
        }
        TripCargoDocument saved = documents.saveAndFlush(TripCargoDocument.builder()
                .id(ids.next())
                .tripId(tripId)
                .documentId(documentId)
                .kind(request.kind())
                .source(CargoDocumentSource.TRIP_SUPPLEMENT)
                .objectKey(key)
                .uploadedBy(accountId)
                .uploadedAt(clock.instant())
                .build());
        return CargoDocumentResponse.from(saved);
    }

    /** One document's bytes. The row must belong to this trip, so an id from another trip reads as not found. */
    @Transactional(readOnly = true)
    public CargoFile fileOf(UUID accountId, AccountRole role, UUID tripId, UUID documentRowId) {
        Trip trip = loadTrip(tripId);
        requireShipperOrAdmin(accountId, role, trip);
        TripCargoDocument row = documents.findByIdAndTripId(documentRowId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chứng từ không tồn tại"));
        return fetch(row.getObjectKey());
    }

    /** Reads one stored document through the private proxy. Any failure is a service error, never an empty file. */
    CargoFile fetch(String objectKey) {
        if (!CargoObjectKeys.isValid(objectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chứng từ không tồn tại");
        }
        return media.fetch(objectKey)
                .map(fetched -> new CargoFile(fetched.bytes(), fetched.contentType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Chưa tải được tệp chứng từ. Vui lòng thử lại sau."));
    }

    Trip loadTrip(UUID tripId) {
        return trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    static void requireShipperOrAdmin(UUID accountId, AccountRole role, Trip trip) {
        boolean allowed = role == AccountRole.ADMIN || (role == AccountRole.SHIPPER && accountId.equals(trip.getShipperId()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ chủ hàng của chuyến hoặc quản trị viên được xem chứng từ giá trị hàng");
        }
    }
}
