package iuh.fit.se.contractservice.service.routing;

/** Small geodesy helpers on [longitude, latitude] pairs (GeoJSON order). */
final class Geo {
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private Geo() {
    }

    static double meters(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[1]), lat2 = Math.toRadians(b[1]);
        double dLat = lat2 - lat1, dLon = Math.toRadians(b[0] - a[0]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Distance from p to segment ab, using a local equirectangular projection around p. */
    static double pointToSegmentMeters(double[] p, double[] a, double[] b) {
        double k = Math.cos(Math.toRadians(p[1]));
        double ax = (a[0] - p[0]) * k, ay = a[1] - p[1];
        double bx = (b[0] - p[0]) * k, by = b[1] - p[1];
        double dx = bx - ax, dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.toRadians(Math.sqrt(cx * cx + cy * cy)) * EARTH_RADIUS_METERS;
    }

    static double length(java.util.List<double[]> line) {
        double total = 0;
        for (int i = 1; i < line.size(); i++) total += meters(line.get(i - 1), line.get(i));
        return total;
    }
}
