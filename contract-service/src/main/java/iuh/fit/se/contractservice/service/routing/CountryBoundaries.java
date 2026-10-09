package iuh.fit.se.contractservice.service.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coarse national boundaries (see resources/routing/NOTICE.md). Used only to reject a planned route that
 * runs deep inside a neighbouring country; a point inside Vietnam, or near a border within the data's
 * coarseness, never counts as a crossing. Routes are never cut or redrawn with this data.
 */
public final class CountryBoundaries {
    /** Depth inside the neighbour's polygon that counts as crossing; covers the simplified data's error. */
    static final double CROSSING_DEPTH_METERS = 2_000;
    private static final String HOME = "VNM";

    private final Map<String, List<double[][][]>> countries;

    CountryBoundaries(Map<String, List<double[][][]>> countries) {
        this.countries = countries;
    }

    public static CountryBoundaries load(ObjectMapper json) {
        try (InputStream in = CountryBoundaries.class.getResourceAsStream("/routing/country-boundaries.json")) {
            if (in == null) return new CountryBoundaries(Map.of());
            JsonNode root = json.readTree(in);
            Map<String, List<double[][][]>> parsed = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                List<double[][][]> polygons = new ArrayList<>();
                for (JsonNode polygon : entry.getValue()) {
                    double[][][] rings = new double[polygon.size()][][];
                    for (int r = 0; r < polygon.size(); r++) {
                        JsonNode ring = polygon.get(r);
                        rings[r] = new double[ring.size()][];
                        for (int i = 0; i < ring.size(); i++) {
                            rings[r][i] = new double[]{ring.get(i).get(0).asDouble(), ring.get(i).get(1).asDouble()};
                        }
                    }
                    polygons.add(rings);
                }
                parsed.put(entry.getKey(), polygons);
            });
            return new CountryBoundaries(parsed);
        } catch (IOException exception) {
            throw new IllegalStateException("Country boundaries resource is unreadable", exception);
        }
    }

    public boolean available() {
        return countries.containsKey(HOME) && countries.size() > 1;
    }

    /** Neighbour code when some route point is outside Vietnam and deep inside that neighbour. */
    public Optional<String> crossedNeighbour(List<double[]> lonLat) {
        if (!available()) return Optional.empty();
        List<double[][][]> home = countries.get(HOME);
        for (double[] point : lonLat) {
            if (inside(home, point)) continue;
            for (var entry : countries.entrySet()) {
                if (HOME.equals(entry.getKey())) continue;
                if (inside(entry.getValue(), point)
                        && distanceToBoundary(entry.getValue(), point) > CROSSING_DEPTH_METERS) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    static boolean inside(List<double[][][]> polygons, double[] point) {
        for (double[][][] polygon : polygons) {
            if (!ringContains(polygon[0], point)) continue;
            boolean inHole = false;
            for (int h = 1; h < polygon.length && !inHole; h++) inHole = ringContains(polygon[h], point);
            if (!inHole) return true;
        }
        return false;
    }

    private static boolean ringContains(double[][] ring, double[] p) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
            if ((yi > p[1]) != (yj > p[1]) && p[0] < (xj - xi) * (p[1] - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }

    private static double distanceToBoundary(List<double[][][]> polygons, double[] p) {
        double best = Double.MAX_VALUE;
        for (double[][][] polygon : polygons) {
            for (double[][] ring : polygon) {
                for (int i = 1; i < ring.length; i++) best = Math.min(best, Geo.pointToSegmentMeters(p, ring[i - 1], ring[i]));
            }
        }
        return best;
    }
}
