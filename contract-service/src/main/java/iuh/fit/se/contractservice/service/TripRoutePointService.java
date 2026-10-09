package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripRouteLocation;
import iuh.fit.se.contractservice.domain.entity.TripRoutePointAudit;
import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.TripRoutePoint;
import iuh.fit.se.contractservice.dto.UpdateTripRoutePointsRequest;
import iuh.fit.se.contractservice.repository.TripRepository;
import iuh.fit.se.contractservice.repository.TripRoutePointAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class TripRoutePointService {
    private final TripRepository trips;
    private final TripRoutePointAuditRepository audits;

    @Transactional
    public Trip update(UUID accountId, AccountRole role, UUID tripId, UpdateTripRoutePointsRequest request) {
        if (role != AccountRole.SHIPPER) throw error(HttpStatus.FORBIDDEN, "Only the owning shipper can confirm route points");
        Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Trip not found"));
        if (!accountId.equals(trip.getShipperId())) throw error(HttpStatus.FORBIDDEN, "Trip does not belong to this shipper");
        if (trip.getStatus() != TripStatus.WAITING_PICKUP)
            throw error(HttpStatus.CONFLICT, "Route points are locked after pickup or cancellation");
        if (request.version() == null || request.version() < 0 || !request.isPinnedRoute()
                || !validSource(request.pickupPoint()) || !validSource(request.deliveryPoint()))
            throw error(HttpStatus.BAD_REQUEST, "Both route points require valid confirmed coordinates and version");
        if (request.version() != trip.getRouteVersion())
            throw error(HttpStatus.CONFLICT, "Route points changed; reload the trip before confirming again");

        TripRouteLocation previousPickup = trip.getPickupPoint(), previousDelivery = trip.getDeliveryPoint();
        long nextVersion = trip.getRouteVersion() + 1;
        audits.save(TripRoutePointAudit.builder().tripId(tripId).actorId(accountId).routeVersion(nextVersion)
                .previousPickupLatitude(previousPickup == null ? null : previousPickup.getLatitude())
                .previousPickupLongitude(previousPickup == null ? null : previousPickup.getLongitude())
                .previousDeliveryLatitude(previousDelivery == null ? null : previousDelivery.getLatitude())
                .previousDeliveryLongitude(previousDelivery == null ? null : previousDelivery.getLongitude())
                .pickupLatitude(request.pickupPoint().latitude()).pickupLongitude(request.pickupPoint().longitude())
                .deliveryLatitude(request.deliveryPoint().latitude()).deliveryLongitude(request.deliveryPoint().longitude())
                .confirmedAt(Instant.now()).build());
        trip.setPickupPoint(pinned(previousPickup, trip.getPickupLocation(), request.pickupPoint()));
        trip.setDeliveryPoint(pinned(previousDelivery, trip.getDeliveryLocation(), request.deliveryPoint()));
        trip.setRouteVersion(nextVersion);
        return trips.save(trip);
    }

    private TripRouteLocation pinned(TripRouteLocation original, String signedLocation, TripRoutePoint point) {
        // Confirming map pins never edits contractual addresses or warehouse labels.
        return new TripRouteLocation(point.latitude(), point.longitude(), original == null ? null : original.getLabel(),
                original == null ? signedLocation : original.getAddress(), "USER_CONFIRMED");
    }

    private boolean validSource(TripRoutePoint point) {
        return point.source() == null || point.source().equals("USER_CONFIRMED");
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
