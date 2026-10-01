package iuh.fit.se.contractservice.service;

import org.springframework.stereotype.Service;

/**
 * Service tính khoảng cách địa lý bằng công thức Haversine.
 *
 * <p>Công thức:
 * <pre>
 *   a = sin²(Δlat/2) + cos(lat1)·cos(lat2)·sin²(Δlng/2)
 *   c = 2·atan2(√a, √(1−a))
 *   d = R · c          (R = 6371 km)
 * </pre>
 * Độ chính xác: ±0.3% — đủ dùng cho bài toán geo-fencing logistics.
 */
@Service
public class GeoFencingService {

    /** Bán kính Trái Đất trung bình (km). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Tính khoảng cách giữa 2 điểm địa lý (km) theo công thức Haversine.
     *
     * @param lat1 Vĩ độ điểm 1 (degrees)
     * @param lng1 Kinh độ điểm 1 (degrees)
     * @param lat2 Vĩ độ điểm 2 (degrees)
     * @param lng2 Kinh độ điểm 2 (degrees)
     * @return Khoảng cách tính bằng km (≥ 0)
     */
    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double sinHalfLat = Math.sin(dLat / 2);
        double sinHalfLng = Math.sin(dLng / 2);

        double a = sinHalfLat * sinHalfLat
                + Math.cos(Math.toRadians(lat1))
                  * Math.cos(Math.toRadians(lat2))
                  * sinHalfLng * sinHalfLng;

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Kiểm tra xem vị trí hiện tại có nằm trong vùng geo-fence không.
     *
     * @param currentLat Vĩ độ hiện tại
     * @param currentLng Kinh độ hiện tại
     * @param targetLat  Vĩ độ cột mốc
     * @param targetLng  Kinh độ cột mốc
     * @param radiusKm   Bán kính cho phép (km)
     * @return {@code true} nếu tài xế nằm trong vùng hợp lệ
     */
    public boolean isWithinRadius(
            double currentLat, double currentLng,
            double targetLat, double targetLng,
            double radiusKm
    ) {
        return calculateDistanceKm(currentLat, currentLng, targetLat, targetLng) <= radiusKm;
    }
}
