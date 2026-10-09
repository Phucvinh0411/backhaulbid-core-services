package iuh.fit.se.contractservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateTripRoutePointsRequest(@NotNull @PositiveOrZero Long version,
                                          @NotNull @Valid TripRoutePoint pickupPoint,
                                          @NotNull @Valid TripRoutePoint deliveryPoint) {
    @AssertTrue(message = "Both pickup and delivery points need confirmed coordinates")
    public boolean isPinnedRoute() {
        return pickupPoint != null && deliveryPoint != null
                && pickupPoint.latitude() != null && deliveryPoint.latitude() != null
                && pickupPoint.isCoordinatePairValid() && deliveryPoint.isCoordinatePairValid();
    }
}
