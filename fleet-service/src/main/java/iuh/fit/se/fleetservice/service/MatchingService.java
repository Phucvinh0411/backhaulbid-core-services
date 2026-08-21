package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class MatchingService {

    private static final double FORBIDDEN_COST = 1e9; // Giá trị lớn đại diện cho cặp cấm
    private static final double EPS = 1e-9;

    /**
     * Giả lập Entity Order cho bài toán ghép cặp (Thường sẽ được gọi qua FeignClient từ OrderService)
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Order {
        public String id;
        public double pickupLat;
        public double pickupLng;
        public LocalDateTime pickupTime;
    }

    /**
     * DTO kết quả cặp ghép giữa Xe rỗng và Đơn hàng
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MatchPair {
        private EmptyRoute emptyRoute;
        private Order order;
        private double matchScore;
    }

    /**
     * DTO kết quả tổng hợp toàn bộ bài toán ghép cặp (Matching Solution)
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MatchingResult {
        private List<MatchPair> matchedPairs = new ArrayList<>();
        private List<Order> unmatchedOrders = new ArrayList<>();
        private List<EmptyRoute> unmatchedRoutes = new ArrayList<>();
        private double totalScore;
    }

    /**
     * Hàm tính khoảng cách đường chim bay (Haversine Formula) giữa 2 tọa độ (km)
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
     * 1. Hàm xây dựng Ma trận Trọng số (Weight Matrix) [Kích thước: numRoutes x numOrders]
     * Trọng số càng cao thể hiện độ phù hợp càng lớn.
     */
    public double[][] buildWeightMatrix(List<Order> orders, List<EmptyRoute> emptyRoutes) {
        int numRoutes = emptyRoutes.size();
        int numOrders = orders.size();
        double[][] weightMatrix = new double[numRoutes][numOrders];

        for (int i = 0; i < numRoutes; i++) {
            EmptyRoute route = emptyRoutes.get(i);
            
            for (int j = 0; j < numOrders; j++) {
                Order order = orders.get(j);

                // 1.1 Tính TimeScore (Trọng số Thời gian)
                long timeDiffMinutes = Duration.between(route.getExpectedEmptyTime(), order.pickupTime).toMinutes();
                
                double timeScore = 0.0;
                if (timeDiffMinutes < 0) {
                    // Trễ giờ -> Khả thi ghép cặp = 0 (Cặp cấm)
                    timeScore = 0.0;
                } else {
                    // Chờ 0 phút -> 100 điểm. Chờ > 24 tiếng (1440 phút) -> điểm về 0.
                    timeScore = Math.max(0, 100 - (timeDiffMinutes / 14.4)); 
                }

                if (timeScore <= 0) {
                    weightMatrix[i][j] = 0;
                    continue;
                }

                // 1.2 Tính SpaceScore (Trọng số Khoảng cách)
                double distance = calculateHaversineDistance(
                        route.getLatitude(), route.getLongitude(),
                        order.pickupLat, order.pickupLng
                );
                
                // Khoảng cách 0km -> 100 điểm. > 100km -> 0 điểm.
                double spaceScore = Math.max(0, 100 - distance);

                if (spaceScore <= 0) {
                    weightMatrix[i][j] = 0;
                    continue;
                }

                // 1.3 Tổng hợp Trọng số (W) theo hệ số: W = 0.6 * SpaceScore + 0.4 * TimeScore
                double totalWeight = (0.6 * spaceScore) + (0.4 * timeScore);
                weightMatrix[i][j] = totalWeight;
                
                log.debug("Xe {} ghép Đơn {} -> SpaceScore: {}, TimeScore: {}, Total: {}", 
                        route.getTruckId(), order.id, spaceScore, timeScore, totalWeight);
            }
        }
        
        return weightMatrix;
    }

    /**
     * 2. Tiền xử lý: Chuyển đổi Ma trận Trọng số (Max Weight) sang Ma trận Chi phí (Min Cost) K x K
     * Xử lý chính xác các cặp cấm (FORBIDDEN_COST) và đệm đỉnh ảo (Dummy Nodes).
     */
    public double[][] buildCostMatrix(double[][] weightMatrix, int numRoutes, int numOrders) {
        int K = Math.max(numRoutes, numOrders);
        if (K == 0) return new double[0][0];

        // Tìm maxWeight thực tế từ ma trận trọng số
        double maxWeight = 0.0;
        for (int i = 0; i < numRoutes; i++) {
            for (int j = 0; j < numOrders; j++) {
                if (weightMatrix[i][j] > maxWeight) {
                    maxWeight = weightMatrix[i][j];
                }
            }
        }

        double[][] costMatrix = new double[K][K];

        for (int i = 0; i < K; i++) {
            for (int j = 0; j < K; j++) {
                if (i < numRoutes && j < numOrders) {
                    double weight = weightMatrix[i][j];
                    if (weight > 0) {
                        // Cặp thực tế hợp lệ: C = maxWeight - W
                        costMatrix[i][j] = maxWeight - weight;
                    } else {
                        // Cặp cấm (W <= 0): Gán giá trị vô cùng lớn
                        costMatrix[i][j] = FORBIDDEN_COST;
                    }
                } else {
                    // Ô chứa đỉnh ảo (Dummy Nodes): Gán bằng 0 để tối ưu tốc độ thuật toán Hungarian
                    costMatrix[i][j] = 0.0;
                }
            }
        }

        return costMatrix;
    }

    /**
     * 3. Thực thi thuật toán ghép cặp tối ưu (Maximum Weight Bipartite Matching)
     * Trả về danh sách các cặp được ghép kèm theo điểm số và thống kê đơn/xe không ghép được.
     */
    public MatchingResult findOptimalMatching(List<Order> orders, List<EmptyRoute> emptyRoutes) {
        MatchingResult result = new MatchingResult();

        if (orders == null || orders.isEmpty() || emptyRoutes == null || emptyRoutes.isEmpty()) {
            if (orders != null) result.setUnmatchedOrders(new ArrayList<>(orders));
            if (emptyRoutes != null) result.setUnmatchedRoutes(new ArrayList<>(emptyRoutes));
            return result;
        }

        int numRoutes = emptyRoutes.size();
        int numOrders = orders.size();

        // Bước 1: Xây dựng ma trận trọng số
        double[][] weightMatrix = buildWeightMatrix(orders, emptyRoutes);

        // Bước 2: Chuyển đổi sang ma trận chi phí K x K
        double[][] costMatrix = buildCostMatrix(weightMatrix, numRoutes, numOrders);
        int K = costMatrix.length;

        // Bước 3: Chạy thuật toán Hungarian O(K^3)
        int[] assignment = HungarianSolver.solve(costMatrix);

        // Bước 4: Hậu xử lý kết quả thu được từ thuật toán
        Set<Integer> matchedOrdersSet = new HashSet<>();
        Set<Integer> matchedRoutesSet = new HashSet<>();
        double totalScore = 0.0;

        for (int i = 0; i < numRoutes; i++) {
            int j = assignment[i];

            // Kiểm tra xem j có nằm trong dải cột thực tế không và có phải cặp hợp lệ không
            if (j >= 0 && j < numOrders) {
                double weight = weightMatrix[i][j];
                double cost = costMatrix[i][j];

                if (weight > 0 && cost < FORBIDDEN_COST / 2) {
                    EmptyRoute route = emptyRoutes.get(i);
                    Order order = orders.get(j);

                    result.getMatchedPairs().add(new MatchPair(route, order, weight));
                    matchedRoutesSet.add(i);
                    matchedOrdersSet.add(j);
                    totalScore += weight;
                }
            }
        }

        result.setTotalScore(totalScore);

        // Thống kê các Xe rỗng chưa được ghép
        for (int i = 0; i < numRoutes; i++) {
            if (!matchedRoutesSet.contains(i)) {
                result.getUnmatchedRoutes().add(emptyRoutes.get(i));
            }
        }

        // Thống kê các Đơn hàng chưa được ghép
        for (int j = 0; j < numOrders; j++) {
            if (!matchedOrdersSet.contains(j)) {
                result.getUnmatchedOrders().add(orders.get(j));
            }
        }

        log.info("Ghép cặp hoàn tất: Đã ghép {}/{} xe rỗng với {}/{} đơn hàng. Tổng điểm: {}",
                matchedRoutesSet.size(), numRoutes, matchedOrdersSet.size(), numOrders, totalScore);

        return result;
    }

    /**
     * Thư viện Hungarian Algorithm (Kuhn-Munkres) tối ưu O(K^3)
     */
    public static class HungarianSolver {
        private static final double INF = 1e12;

        public static int[] solve(double[][] costMatrix) {
            int n = costMatrix.length;
            if (n == 0) return new int[0];

            double[] u = new double[n + 1];
            double[] v = new double[n + 1];
            int[] p = new int[n + 1];
            int[] way = new int[n + 1];

            for (int i = 1; i <= n; i++) {
                p[0] = i;
                int j0 = 0;
                double[] minv = new double[n + 1];
                Arrays.fill(minv, INF);
                boolean[] used = new boolean[n + 1];

                do {
                    used[j0] = true;
                    int i0 = p[j0];
                    double delta = INF;
                    int j1 = 0;

                    for (int j = 1; j <= n; j++) {
                        if (!used[j]) {
                            double cur = costMatrix[i0 - 1][j - 1] - u[i0] - v[j];
                            if (cur < minv[j]) {
                                minv[j] = cur;
                                way[j] = j0;
                            }
                            if (minv[j] < delta) {
                                delta = minv[j];
                                j1 = j;
                            }
                        }
                    }

                    for (int j = 0; j <= n; j++) {
                        if (used[j]) {
                            u[p[j]] += delta;
                            v[j] -= delta;
                        } else {
                            minv[j] -= delta;
                        }
                    }
                    j0 = j1;
                } while (p[j0] != 0);

                do {
                    int j1 = way[j0];
                    p[j0] = p[j1];
                    j0 = j1;
                } while (j0 != 0);
            }

            int[] assignment = new int[n];
            Arrays.fill(assignment, -1);
            for (int j = 1; j <= n; j++) {
                if (p[j] > 0) {
                    assignment[p[j] - 1] = j - 1;
                }
            }

            return assignment;
        }
    }
}
