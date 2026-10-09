package iuh.fit.se.contractservice.service.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Douglas-Peucker simplification with a tolerance in meters. Endpoints are always kept. Unlike taking
 * every n-th point, the result never moves more than the tolerance away from the provider's line, so
 * long curved routes keep their shape instead of cutting across bends.
 */
public final class RouteSimplifier {
    /** 15 m keeps the route on the drawn road at city zoom; larger only when the point budget needs it. */
    public static final double BASE_TOLERANCE_METERS = 15;
    public static final int MAX_POINTS = 4_000;

    private RouteSimplifier() {
    }

    public static Result simplify(List<double[]> line) {
        double tolerance = BASE_TOLERANCE_METERS;
        List<double[]> result = douglasPeucker(line, tolerance);
        while (result.size() > MAX_POINTS) {
            tolerance *= 2;
            result = douglasPeucker(line, tolerance);
        }
        return new Result(result, tolerance);
    }

    public record Result(List<double[]> points, double toleranceMeters) {
    }

    static List<double[]> douglasPeucker(List<double[]> line, double tolerance) {
        if (line.size() < 3) return new ArrayList<>(line);
        boolean[] keep = new boolean[line.size()];
        keep[0] = true;
        keep[line.size() - 1] = true;
        // Iterative to avoid deep recursion on routes with tens of thousands of points.
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{0, line.size() - 1});
        while (!stack.isEmpty()) {
            int[] span = stack.pop();
            int start = span[0], end = span[1];
            double farthest = -1;
            int index = -1;
            for (int i = start + 1; i < end; i++) {
                double distance = Geo.pointToSegmentMeters(line.get(i), line.get(start), line.get(end));
                if (distance > farthest) {
                    farthest = distance;
                    index = i;
                }
            }
            if (index > 0 && farthest > tolerance) {
                keep[index] = true;
                stack.push(new int[]{start, index});
                stack.push(new int[]{index, end});
            }
        }
        List<double[]> simplified = new ArrayList<>();
        for (int i = 0; i < line.size(); i++) if (keep[i]) simplified.add(line.get(i));
        return simplified;
    }
}
