package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class MatchingService {

    /**
     * Giả lập Entity Order cho bài toán ghép cặp (Thường sẽ được gọi qua FeignClient từ OrderService)
     */
    public static class Order {
        public String id;
        public double pickupLat;
        public double pickupLng;
        public LocalDateTime pickupTime;

        public Order(String id, double pickupLat, double pickupLng, LocalDateTime pickupTime) {
            this.id = id;
            this.pickupLat = pickupLat;
            this.pickupLng = pickupLng;
            this.pickupTime = pickupTime;
        }
    }

    /**
     * Hàm tính khoảng cách đường chim bay (Haversine Formula) giữa 2 tọa độ (km)
     * Công thức này được sử dụng rộng rãi trong các hệ thống GIS và Logistics để tính khoảng cách thực tế trên bề mặt cầu của Trái Đất.
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Bán kính Trái Đất (km)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Trả về khoảng cách theo đơn vị km
    }

    /**
     * Hàm xây dựng Ma trận Trọng số (Weight Matrix) để đưa vào thuật toán Hungarian (Kuhn-Munkres).
     * Thuật toán Hungarian yêu cầu một ma trận chi phí (hoặc trọng số) NxM để tối ưu hóa việc ghép cặp (Bipartite Matching).
     * Ở đây ta tính ĐIỂM SỐ (Càng cao càng tốt), do đó sau này khi đưa vào Hungarian Min-Cost, ma trận này cần được đảo dấu hoặc chuyển thành ma trận Chi phí.
     * 
     * @param orders Danh sách Đơn hàng (Jobs)
     * @param emptyRoutes Danh sách Xe đang rỗng (Workers)
     * @return Ma trận trọng số 2 chiều (Mảng 2D) kích thước [số_xe_rỗng][số_đơn_hàng]
     */
    public double[][] buildWeightMatrix(List<Order> orders, List<EmptyRoute> emptyRoutes) {
        int numRoutes = emptyRoutes.size();
        int numOrders = orders.size();
        double[][] weightMatrix = new double[numRoutes][numOrders];

        // Duyệt qua từng Xe rỗng (i) và ghép thử với từng Đơn hàng (j)
        for (int i = 0; i < numRoutes; i++) {
            EmptyRoute route = emptyRoutes.get(i);
            
            for (int j = 0; j < numOrders; j++) {
                Order order = orders.get(j);

                // 1. Tính TimeScore (Trọng số Thời gian)
                // Yêu cầu: Xe rỗng phải có mặt TRƯỚC thời điểm bốc hàng (pickupTime).
                // Độ lệch thời gian tính bằng phút.
                long timeDiffMinutes = Duration.between(route.getExpectedEmptyTime(), order.pickupTime).toMinutes();
                
                double timeScore = 0.0;
                if (timeDiffMinutes < 0) {
                    // Xe rỗng SAU khi đơn hàng cần bốc -> Trễ giờ -> Khả thi ghép cặp = 0 (Loại ngay lập tức)
                    timeScore = 0.0;
                } else {
                    // Xe rỗng sớm hơn. Càng sát giờ bốc hàng thì TimeScore càng cao (giảm thời gian xe phải chờ đợi "chết" tại bãi).
                    // Giả sử khoảng thời gian chờ tối ưu chấp nhận được là 24 tiếng (1440 phút).
                    // Chờ 0 phút -> Điểm tuyệt đối (100). Chờ > 24 tiếng -> Điểm thấp dần về 0.
                    timeScore = Math.max(0, 100 - (timeDiffMinutes / 14.4)); 
                }

                // Nếu thời gian đã không thoả mãn (timeScore = 0) thì không cần tính tiếp
                if (timeScore == 0) {
                    weightMatrix[i][j] = 0;
                    continue;
                }

                // 2. Tính SpaceScore (Trọng số Không gian - Khoảng cách)
                double distance = calculateHaversineDistance(
                        route.getLatitude(), route.getLongitude(),
                        order.pickupLat, order.pickupLng
                );
                
                // Khoảng cách càng ngắn thì SpaceScore càng cao.
                // Giả sử xe rỗng di chuyển tới điểm bốc hàng tối đa 100km.
                // Nếu khoảng cách = 0km -> Điểm 100. Khoảng cách = 100km -> Điểm 0.
                double spaceScore = Math.max(0, 100 - distance);

                // 3. Tổng hợp Trọng số (W) theo yêu cầu hệ số: W = 0.6 * SpaceScore + 0.4 * TimeScore
                // Ưu tiên Không gian (60%) hơn Thời gian chờ (40%) để tiết kiệm nhiên liệu chạy rỗng.
                double totalWeight = (0.6 * spaceScore) + (0.4 * timeScore);

                // Ghi vào ma trận
                weightMatrix[i][j] = totalWeight;
                
                log.debug("Xe {} ghép Đơn {} -> SpaceScore: {}, TimeScore: {}, Total: {}", 
                        route.getTruckId(), order.id, spaceScore, timeScore, totalWeight);
            }
        }
        
        return weightMatrix;
    }
}
