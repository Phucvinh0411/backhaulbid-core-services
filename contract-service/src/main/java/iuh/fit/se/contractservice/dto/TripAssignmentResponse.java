package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.enums.TripStatus;

import java.util.UUID;

public record TripAssignmentResponse(
        UUID tripId,
        UUID driverId,
        String assignmentPin,
        TripStatus status
) {
}
