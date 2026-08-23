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

    List<EmptyRoute> findByCompanyIdOrderByExpectedEmptyTimeAsc(String companyId);
    
    List<EmptyRoute> findByOriginContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(String origin, String destination, EmptyRouteStatus status);
}
