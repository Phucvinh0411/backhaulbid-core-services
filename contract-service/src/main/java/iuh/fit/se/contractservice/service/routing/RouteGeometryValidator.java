package iuh.fit.se.contractservice.service.routing;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks a provider route before it is shown as the planned road between two warehouses.
 * Thresholds are in meters and each one names what it protects against.
 */
public final class RouteGeometryValidator {
    /** Provider responses above this are refused before parsing (OSRM full overview for ~2,000 km). */
    public static final int MAX_COORDINATES = 50_000;
    /**
     * Consecutive OSRM geometry vertices are normally tens to hundreds of meters apart; a jump above
     * 10 km means a broken or reordered line, not a road.
     */
    static final double MAX_VERTEX_GAP_METERS = 10_000;
    /**
     * A warehouse can sit off the road graph (we saw 660 m in demo data); beyond 2 km the snapped road is
     * a different place than the pin.
     */
    static final double MAX_ANCHOR_SNAP_METERS = 2_000;
    /** A road cannot be shorter than the great-circle distance; 5% covers rounding in the geometry. */
    static final double MIN_LENGTH_RATIO = 0.95;

    private final CountryBoundaries boundaries;

    public RouteGeometryValidator(CountryBoundaries boundaries) {
        this.boundaries = boundaries;
    }

    public sealed interface Outcome permits Accepted, Rejected {
    }

    public record Accepted(List<double[]> points, double lengthMeters, double toleranceMeters) implements Outcome {
    }

    public record Rejected(String reason) implements Outcome {
    }

    /** Parses one OSRM route's GeoJSON geometry into [lon, lat] pairs, or empty when malformed. */
    public static Optional<List<double[]>> parse(JsonNode geometry) {
        if (geometry == null || !"LineString".equals(geometry.path("type").asText())) return Optional.empty();
        JsonNode coordinates = geometry.path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2 || coordinates.size() > MAX_COORDINATES) return Optional.empty();
        List<double[]> line = new ArrayList<>(coordinates.size());
        for (JsonNode pair : coordinates) {
            if (!pair.isArray() || pair.size() != 2 || !pair.get(0).isNumber() || !pair.get(1).isNumber()) return Optional.empty();
            double lon = pair.get(0).asDouble(), lat = pair.get(1).asDouble();
            if (!Double.isFinite(lon) || !Double.isFinite(lat) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return Optional.empty();
            line.add(new double[]{lon, lat});
        }
        return Optional.of(line);
    }

    /**
     * @param from pickup [lon, lat]
     * @param to   delivery [lon, lat]
     */
    public Outcome validate(List<double[]> line, double[] from, double[] to) {
        for (int i = 1; i < line.size(); i++) {
            if (Geo.meters(line.get(i - 1), line.get(i)) > MAX_VERTEX_GAP_METERS) return new Rejected("DISCONTINUOUS");
        }
        if (Geo.meters(line.getFirst(), from) > MAX_ANCHOR_SNAP_METERS) return new Rejected("START_FAR_FROM_PICKUP");
        if (Geo.meters(line.getLast(), to) > MAX_ANCHOR_SNAP_METERS) return new Rejected("END_FAR_FROM_DELIVERY");
        double length = Geo.length(line);
        if (length < MIN_LENGTH_RATIO * Geo.meters(from, to)) return new Rejected("SHORTER_THAN_STRAIGHT_LINE");
        RouteSimplifier.Result simplified = RouteSimplifier.simplify(line);
        Optional<String> crossed = boundaries.crossedNeighbour(simplified.points());
        if (crossed.isPresent()) return new Rejected("CROSSES_BORDER_" + crossed.get());
        return new Accepted(simplified.points(), length, simplified.toleranceMeters());
    }
}
