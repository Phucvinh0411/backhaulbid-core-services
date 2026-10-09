package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned once to the carrier when a driver code is issued. {@code code} is the full "mã nhận chuyến";
 * it is never stored or readable again from any trip API.
 */
public record TripAssignmentResponse(
        UUID tripId,
        UUID driverProfileId,
        UUID grantId,
        String code,
        Instant expiresAt,
        TripStatus status,
        long assignmentVersion
) {
    @Override public String toString() {
        return "TripAssignmentResponse(" + tripId + ", " + grantId + ", <code redacted>)";
    }
}
