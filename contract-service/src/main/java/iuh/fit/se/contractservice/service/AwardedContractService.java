package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Contract;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.AuctionAwardAttemptStatusResponse;
import iuh.fit.se.contractservice.dto.AuctionAwardResponse;
import iuh.fit.se.contractservice.dto.CargoValueDocumentRef;
import iuh.fit.se.contractservice.dto.CreateAuctionAwardRequest;
import iuh.fit.se.contractservice.repository.ContractRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.TripTrackingSessionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AwardedContractService {
    private final TripRepository tripRepository;
    private final ContractRepository contractRepository;
    private final TripTrackingSessionRepository trackingSessions;
    private final EntityManager entityManager;
    private final TripCargoDocumentService cargoDocuments;

    @Transactional
    public AuctionAwardResponse createOrGet(CreateAuctionAwardRequest request) {
        validatePoint(request.pickupPoint()); validatePoint(request.deliveryPoint());
        BigDecimal declaredGoodsValue = request.declaredGoodsValue() == null
                ? null : request.declaredGoodsValue().setScale(2, RoundingMode.HALF_UP);
        List<CargoValueDocumentRef> documents = validatedDocuments(request.valueDocuments());
        String routeSnapshotHash = RouteSnapshotFingerprint.of(request);
        String cargoSnapshotHash = CargoSnapshotFingerprint.of(declaredGoodsValue, documents);
        Trip trip = tripRepository.findByAwardAttemptId(request.awardAttemptId()).orElseGet(() -> {
            Trip newTrip = Trip.builder()
                    .auctionId(request.auctionId())
                    .awardAttemptId(request.awardAttemptId())
                    .winningBidId(request.winningBidId())
                    .shipperId(request.shipperId())
                    .carrierId(request.carrierId())
                    .vehicleId(request.vehicleId())
                    .pickupLocation(request.pickupLocation())
                    .deliveryLocation(request.deliveryLocation())
                    .pickupPoint(request.pickupPoint() == null ? null : request.pickupPoint().snapshot())
                    .deliveryPoint(request.deliveryPoint() == null ? null : request.deliveryPoint().snapshot())
                    .awardRouteSnapshotHash(routeSnapshotHash)
                    .declaredGoodsValue(declaredGoodsValue)
                    .cargoSnapshotHash(cargoSnapshotHash)
                    .agreedPrice(request.agreedPrice())
                    .expectedDeliveryAt(request.expectedDeliveryAt())
                    .depositHoldId(request.depositHoldId())
                    .depositAmount(request.depositAmount())
                    .status(TripStatus.WAITING_PICKUP)
                    .build();
            Trip saved = tripRepository.save(newTrip);
            cargoDocuments.recordAwardSnapshot(saved, documents);
            return saved;
        });

        if (!Objects.equals(trip.getAuctionId(), request.auctionId())
                || !trip.getCarrierId().equals(request.carrierId())
                || !trip.getVehicleId().equals(request.vehicleId())
                || !trip.getShipperId().equals(request.shipperId())
                || !request.winningBidId().equals(trip.getWinningBidId())
                || trip.getAgreedPrice().compareTo(request.agreedPrice()) != 0
                || !java.util.Objects.equals(trip.getExpectedDeliveryAt(), request.expectedDeliveryAt())
                || !java.util.Objects.equals(trip.getDepositHoldId(), request.depositHoldId())
                || !sameAmount(trip.getDepositAmount(), request.depositAmount())
                || !Objects.equals(trip.getPickupLocation(), request.pickupLocation())
                || !Objects.equals(trip.getDeliveryLocation(), request.deliveryLocation())
                || (trip.getAwardRouteSnapshotHash() != null
                    ? !trip.getAwardRouteSnapshotHash().equals(routeSnapshotHash)
                    : hasCoordinates(request.pickupPoint()) || hasCoordinates(request.deliveryPoint()))) {
            throw new IllegalStateException("Auction award was already created with different winner data");
        }
        if (!cargoSnapshotMatches(trip, cargoSnapshotHash)) {
            throw new IllegalStateException("Auction award was already created with a different cargo snapshot");
        }

        Contract contract = contractRepository.findByTripId(trip.getId()).orElseGet(() -> {
            Contract newContract = Contract.builder()
                    .trip(trip)
                    .shipperId(trip.getShipperId())
                    .carrierId(trip.getCarrierId())
                    .contractCode("AUC-" + request.awardAttemptId())
                    .status(ContractStatus.DRAFT)
                    .signingDeadlineAt(request.signingDeadlineAt() == null
                            ? Instant.now().plus(Duration.ofHours(24))
                            : request.signingDeadlineAt())
                    .build();
            return contractRepository.save(newContract);
        });
        return new AuctionAwardResponse(request.auctionId(), trip.getId(), contract.getId(),
                trip.getExpectedDeliveryAt() != null && trip.getDepositHoldId() != null
                        && trip.getDepositAmount() != null && trip.getDepositAmount().signum() > 0);
    }

    @Transactional(readOnly = true)
    public AuctionAwardAttemptStatusResponse status(String awardAttemptId) {
        Trip trip = tripRepository.findByAwardAttemptId(awardAttemptId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Auction award attempt not found"));
        Contract contract = contractRepository.findByTripId(trip.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Award contract not found"));
        return statusResponse(trip, contract);
    }

    @Transactional
    public AuctionAwardAttemptStatusResponse expireIfDeadlinePassed(String awardAttemptId) {
        Trip trip = tripRepository.findByAwardAttemptId(awardAttemptId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Auction award attempt not found"));
        Contract contract = contractRepository.findByTripIdForUpdate(trip.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Award contract not found"));

        // Keep the contract -> trip lock order used by signing. The lookup above may have
        // loaded an old Trip into this persistence context while another writer held its row.
        trip = tripRepository.findByIdForUpdate(trip.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Auction award trip not found"));
        entityManager.refresh(trip);

        Instant now = Instant.now();
        if ((contract.getStatus() == ContractStatus.DRAFT
                || contract.getStatus() == ContractStatus.WAITING_SIGNATURE)
                && contract.getSigningDeadlineAt() != null
                && !now.isBefore(contract.getSigningDeadlineAt())) {
            contract.setStatus(ContractStatus.EXPIRED);
            contractRepository.save(contract);
            if (trip.getStatus() != TripStatus.CANCELLED && trip.getStatus() != TripStatus.COMPLETED) {
                trip.cancel("Hợp đồng hết hạn do không được ký đúng thời hạn");
                trackingSessions.stopForTrip(trip.getId(), now);
                tripRepository.save(trip);
            }
        }
        return statusResponse(trip, contract);
    }

    private AuctionAwardAttemptStatusResponse statusResponse(Trip trip, Contract contract) {
        boolean carrierSigned = contract.getSignatures() != null && contract.getSignatures().stream()
                .anyMatch(signature -> signature.getRole() == iuh.fit.se.contractservice.domain.enums.AccountRole.CARRIER
                        && signature.getSignedAt() != null);
        boolean shipperSigned = contract.getSignatures() != null && contract.getSignatures().stream()
                .anyMatch(signature -> signature.getRole() == iuh.fit.se.contractservice.domain.enums.AccountRole.SHIPPER
                        && signature.getSignedAt() != null);
        return new AuctionAwardAttemptStatusResponse(
                trip.getAuctionId(), trip.getAwardAttemptId(), trip.getId(), contract.getId(),
                contract.getStatus(), carrierSigned, shipperSigned, contract.getSigningDeadlineAt());
    }

    /**
     * A trip created with a snapshot must be retried with the same one. A trip created before snapshots has none
     * recorded, so only an attempt that carries no cargo matches it.
     */
    private boolean cargoSnapshotMatches(Trip trip, String cargoSnapshotHash) {
        if (trip.getCargoSnapshotHash() != null) {
            return trip.getCargoSnapshotHash().equals(cargoSnapshotHash);
        }
        return CargoSnapshotFingerprint.of(null, List.of()).equals(cargoSnapshotHash);
    }

    private static List<CargoValueDocumentRef> validatedDocuments(List<CargoValueDocumentRef> documents) {
        if (documents == null || documents.isEmpty()) return List.of();
        Set<String> documentIds = new HashSet<>();
        Set<String> objectKeys = new HashSet<>();
        for (CargoValueDocumentRef document : documents) {
            String fileId = CargoObjectKeys.documentIdOf(document.objectKey());
            if (fileId == null || !fileId.equals(document.documentId()) || document.kind() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chứng từ giá trị hàng của phiên đấu giá không hợp lệ");
            }
            if (!documentIds.add(document.documentId()) || !objectKeys.add(document.objectKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chứng từ giá trị hàng bị trùng trong phiên đấu giá");
            }
        }
        return List.copyOf(documents);
    }

    private boolean sameAmount(java.math.BigDecimal current, java.math.BigDecimal requested) {
        return current == null ? requested == null : requested != null && current.compareTo(requested) == 0;
    }

    private void validatePoint(iuh.fit.se.contractservice.dto.TripRoutePoint point) {
        if (point != null && (!point.isCoordinatePairValid()
                || (point.source() != null && !point.source().equals("USER_CONFIRMED"))))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Award route coordinates are invalid");
    }

    private boolean hasCoordinates(iuh.fit.se.contractservice.dto.TripRoutePoint point) {
        return point != null && point.latitude() != null;
    }
}
