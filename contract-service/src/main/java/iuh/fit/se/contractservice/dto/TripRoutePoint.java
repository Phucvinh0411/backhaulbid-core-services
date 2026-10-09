package iuh.fit.se.contractservice.dto;

import iuh.fit.se.contractservice.domain.entity.TripRouteLocation;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TripRoutePoint(Double latitude, Double longitude,
                             @Size(max = 120) String label, @Size(max = 1000) String address,
                             @Pattern(regexp = "USER_CONFIRMED") String source) {
    @AssertTrue(message = "Coordinates must be a complete pair within latitude/longitude bounds")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isCoordinatePairValid() {
        if (latitude == null && longitude == null) return source == null;
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public TripRouteLocation snapshot() {
        return new TripRouteLocation(latitude, longitude, label, address,
                latitude == null ? null : "USER_CONFIRMED");
    }

    public static TripRoutePoint from(TripRouteLocation location) {
        return location == null ? null : new TripRoutePoint(location.getLatitude(), location.getLongitude(),
                location.getLabel(), location.getAddress(), location.getSource());
    }
}
