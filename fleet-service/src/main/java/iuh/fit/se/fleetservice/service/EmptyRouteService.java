package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.repository.EmptyRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmptyRouteService {

    private final EmptyRouteRepository emptyRouteRepository;
    private final iuh.fit.se.fleetservice.repository.VehicleRepository vehicleRepository;

    /**
     * Chủ nhà xe đăng ký tuyến chạy rỗng
     */
    @Transactional
    public EmptyRoute createEmptyRoute(EmptyRouteRequest request) {
        log.info("Đăng ký tuyến chạy rỗng mới cho xe: {}", request.getTruckId());
        
        EmptyRoute emptyRoute = EmptyRoute.builder()
                .truckId(request.getTruckId())
                .companyId(request.getCompanyId())
                .expectedEmptyTime(request.getExpectedEmptyTime())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .status(EmptyRouteStatus.PENDING)
                .build();
                
        return emptyRouteRepository.save(emptyRoute);
    }

    @Transactional(readOnly = true)
    public java.util.List<EmptyRoute> findMatchingRoutes(String origin, String destination, java.util.Map<String, Object> auctionData) {
        log.info("Tìm kiếm xe rỗng chiều nâng cao cho lộ trình: {} -> {}", origin, destination);
        
        String originKeyword = extractProvince(origin);
        String destinationKeyword = extractProvince(destination);
        
        java.util.List<EmptyRoute> potentialMatches = emptyRouteRepository
            .findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
                originKeyword, 
                destinationKeyword, 
                EmptyRouteStatus.PENDING
            );

        // Advanced Criteria Matching
        java.util.List<EmptyRoute> verifiedMatches = new java.util.ArrayList<>();
        
        for (EmptyRoute route : potentialMatches) {
            try {
                // 1. Time Matching
                if (auctionData.containsKey("latestPickup") && auctionData.get("latestPickup") != null) {
                    java.time.LocalDateTime latestPickup = java.time.LocalDateTime.parse(
                        auctionData.get("latestPickup").toString().replace("Z", "")
                    );
                    if (route.getExpectedEmptyTime().isAfter(latestPickup)) {
                        log.debug("Loại route {} do thời gian rỗng ({}) trễ hơn latestPickup ({})", route.getId(), route.getExpectedEmptyTime(), latestPickup);
                        continue;
                    }
                }

                // 2. Vehicle Specs Matching
                if (auctionData.containsKey("weight") && auctionData.get("weight") != null) {
                    java.math.BigDecimal requiredWeight = new java.math.BigDecimal(auctionData.get("weight").toString());
                    
                    // Lấy thông tin phương tiện thông qua biển số
                    // (Lưu ý: Bạn có thể Inject VehicleRepository vào EmptyRouteService)
                    java.util.Optional<iuh.fit.se.fleetservice.domain.entity.Vehicle> vehicleOpt = 
                        vehicleRepository.findByLicensePlateIgnoreCase(route.getTruckId());
                        
                    if (vehicleOpt.isPresent()) {
                        iuh.fit.se.fleetservice.domain.entity.Vehicle vehicle = vehicleOpt.get();
                        
                        // Kiểm tra tải trọng
                        if (vehicle.getPayloadCapacity().compareTo(requiredWeight) < 0) {
                            log.debug("Loại route {} do tải trọng xe ({}) nhỏ hơn yêu cầu ({})", route.getId(), vehicle.getPayloadCapacity(), requiredWeight);
                            continue;
                        }

                        // (Tùy chọn) Kiểm tra loại xe
                        /* 
                        if (auctionData.containsKey("vehicleTypeRequired") && auctionData.get("vehicleTypeRequired") != null) {
                           String reqType = auctionData.get("vehicleTypeRequired").toString();
                           // Thực hiện ánh xạ (Mapping) enum nếu cần
                        }
                        */
                    } else {
                        // Nếu không tìm thấy xe trong DB, tạm bỏ qua hoặc cho phép tùy quy trình
                        log.warn("Không tìm thấy thông tin xe {} trong database", route.getTruckId());
                    }
                }
                
                verifiedMatches.add(route);
            } catch (Exception e) {
                log.error("Lỗi khi đánh giá tiêu chí nâng cao cho route {}: {}", route.getId(), e.getMessage());
                // Nếu lỗi parse, tạm thời vẫn add (hoặc skip tùy logic)
                verifiedMatches.add(route);
            }
        }
        
        return verifiedMatches;
    }
    
    private String extractProvince(String fullLocation) {
        if (fullLocation == null) return "";
        String[] parts = fullLocation.split("-");
        // Giả sử province nằm ở phần đầu tiên hoặc lấy toàn bộ chuỗi nếu không có dấu -
        return parts[0].trim();
    }
}
