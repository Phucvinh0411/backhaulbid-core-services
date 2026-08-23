package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import iuh.fit.se.fleetservice.dto.EmptyRouteRequest;
import iuh.fit.se.fleetservice.repository.EmptyRouteRepository;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmptyRouteServiceTest {

    @Mock
    private EmptyRouteRepository emptyRouteRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private EmptyRouteService emptyRouteService;

    @Test
    void givenCarrierId_whenListMine_thenOnlyThatCompanyRoutesAreReturned() {
        String companyId = UUID.randomUUID().toString();
        List<EmptyRoute> routes = List.of(EmptyRoute.builder().companyId(companyId).status(EmptyRouteStatus.PENDING).build());
        when(emptyRouteRepository.findByCompanyIdOrderByExpectedEmptyTimeAsc(companyId)).thenReturn(routes);

        List<EmptyRoute> result = emptyRouteService.listMine(companyId);

        assertEquals(routes, result);
        verify(emptyRouteRepository).findByCompanyIdOrderByExpectedEmptyTimeAsc(companyId);
    }

    @Test
    void givenValidRequest_whenCreateMine_thenOwnerHeaderIsPersistedAsCompanyId() {
        String ownerId = UUID.randomUUID().toString();
        EmptyRouteRequest request = EmptyRouteRequest.builder()
                .truckId("51H-12345")
                .companyId("spoofed-company")
                .expectedEmptyTime(LocalDateTime.now().plusHours(2))
                .latitude(10.8)
                .longitude(106.7)
                .build();
        when(emptyRouteRepository.save(any(EmptyRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmptyRoute result = emptyRouteService.createEmptyRoute(request, ownerId);

        assertEquals(ownerId, result.getCompanyId());
        assertEquals(EmptyRouteStatus.PENDING, result.getStatus());
        verify(emptyRouteRepository).save(any(EmptyRoute.class));
    }
}
