package iuh.fit.se.contractservice.service.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Via points on National Route 1A (QL1A), north to south, used when the provider's shortest road between two
 * Vietnamese warehouses leaves the country. Domestic freight does not cross into Laos or Cambodia (customs and
 * cross-border permits), but a car profile over a regional map often does, for example Hà Nội → TP.HCM.
 */
public final class VietnamCorridor {
    /** [longitude, latitude] on the QL1A carriageway, not the city centre, so the provider snaps onto QL1A itself. */
    static final List<double[]> QL1A = List.of(
            new double[]{105.776, 19.807},  // Thanh Hóa
            new double[]{105.672, 18.699},  // Vinh
            new double[]{106.618, 17.468},  // Đồng Hới
            new double[]{107.100, 16.818},  // Đông Hà
            new double[]{108.180, 16.020},  // Đà Nẵng (Hòa Cầm)
            new double[]{108.800, 15.120},  // Quảng Ngãi
            new double[]{109.130, 13.810},  // Quy Nhơn (Phú Tài)
            new double[]{109.100, 12.270},  // Nha Trang (Diên Khánh)
            new double[]{108.080, 10.970}   // Phan Thiết
    );
    /**
     * A via point closer than this (in latitude) to A or B is skipped: the provider already reaches it from that
     * warehouse inside Vietnam, and forcing it could pull a short leg away from the warehouse. About 33 km.
     */
    static final double END_MARGIN_DEGREES = 0.3;

    private VietnamCorridor() {
    }

    /**
     * Via points strictly between the latitudes of from and to, ordered from {@code from} towards {@code to}.
     * Empty when the two warehouses are too close in latitude to need a corridor.
     *
     * @param from pickup [lon, lat]
     * @param to   delivery [lon, lat]
     */
    public static List<double[]> between(double[] from, double[] to) {
        double north = Math.max(from[1], to[1]) - END_MARGIN_DEGREES;
        double south = Math.min(from[1], to[1]) + END_MARGIN_DEGREES;
        List<double[]> via = new ArrayList<>();
        for (double[] point : QL1A) {
            if (point[1] < north && point[1] > south) via.add(point);
        }
        if (from[1] < to[1]) Collections.reverse(via);
        return via;
    }
}
