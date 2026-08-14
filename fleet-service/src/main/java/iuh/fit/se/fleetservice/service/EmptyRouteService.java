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
                .status(EmptyRouteStatus.PENDING)
                .build();
                
        return emptyRouteRepository.save(emptyRoute);
    }
}
