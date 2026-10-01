package iuh.fit.se.fleetservice.repository;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmptyRouteRepository extends JpaRepository<EmptyRoute, UUID> {

    List<EmptyRoute> findByStatus(EmptyRouteStatus status);

    /** Lấy tất cả tuyến rỗng theo trạng thái, sắp xếp theo thời gian xe rỗng (dùng cho GIS matching) */
    List<EmptyRoute> findByStatusOrderByExpectedEmptyTimeAsc(EmptyRouteStatus status);

    List<EmptyRoute> findByCompanyIdOrderByExpectedEmptyTimeAsc(String companyId);

    /** Giữ lại query cũ cho backward compatibility (text-based matching) */
    List<EmptyRoute> findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
            String origin, String destination, EmptyRouteStatus status);
}
