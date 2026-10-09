package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.JourneyEvent;
import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.JourneyEventType;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.repository.JourneyEventRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripClaimService {
    private final TripRepository tripRepository;
    private final JourneyEventRepository journeyEventRepository;

    // A rejected PIN must commit its attempt counter, while every other error rolls back.
    @Transactional(noRollbackFor = InvalidPinException.class)
    public Trip claim(UUID accountId, AccountRole role, UUID tripId, String pin) {
        if (role != AccountRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver role required");
        }
        if (pin == null || !pin.matches("[0-9]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN must contain exactly six digits");
        }
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        if (accountId.equals(trip.getDriverAccountId())) return trip; // Safe retry after a lost response.
        if (trip.getDriverAccountId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip has already been claimed");
        }
        if (trip.getStatus() != TripStatus.WAITING_PICKUP || trip.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip is not awaiting a driver claim");
        }
        if (trip.getAssignmentPinHash() == null || trip.getAssignmentPinExpiresAt() == null
                || !trip.getAssignmentPinExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment PIN expired; ask the carrier to reassign");
        }
        if (trip.getAssignmentPinAttempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Assignment PIN locked; ask the carrier to reassign");
        }
        if (!MessageDigest.isEqual(hash(pin).getBytes(StandardCharsets.UTF_8),
                trip.getAssignmentPinHash().getBytes(StandardCharsets.UTF_8))) {
            trip.setAssignmentPinAttempts(trip.getAssignmentPinAttempts() + 1);
            tripRepository.saveAndFlush(trip);
            throw new InvalidPinException();
        }
        trip.setDriverAccountId(accountId);
        trip.setAssignmentPinHash(null);
        trip.setAssignmentPinExpiresAt(null);
        tripRepository.save(trip);
        journeyEventRepository.save(JourneyEvent.builder().trip(trip).actorId(accountId)
                .eventType(JourneyEventType.DRIVER_ACCEPTED).status(trip.getStatus())
                .note("Tài xế nhận chuyến bằng PIN phân công").build());
        return trip;
    }

    private static String hash(String pin) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static class InvalidPinException extends ResponseStatusException {
        public InvalidPinException() {
            super(HttpStatus.BAD_REQUEST, "Invalid assignment PIN");
        }
    }
}
