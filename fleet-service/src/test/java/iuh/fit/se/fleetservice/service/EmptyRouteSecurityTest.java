package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.controller.EmptyRouteController;
import iuh.fit.se.fleetservice.controller.InternalEmptyRouteController;
import iuh.fit.se.fleetservice.domain.entity.EmptyRoute;
import iuh.fit.se.fleetservice.domain.enums.EmptyRouteStatus;
import iuh.fit.se.fleetservice.dto.EmptyRouteResponse;
import iuh.fit.se.fleetservice.mapper.EmptyRouteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmptyRouteSecurityTest {

    @Mock private EmptyRouteService emptyRouteService;
    @Mock private IdentityVerificationClient identityVerificationClient;
    private final EmptyRouteMapper emptyRouteMapper = Mappers.getMapper(EmptyRouteMapper.class);

    @Test
    void carrierOptimalMatchingRequiresVerificationAndScopesToCallerCompany() {
        EmptyRouteController controller = new EmptyRouteController(
                emptyRouteService, identityVerificationClient, emptyRouteMapper);
        UUID carrierId = UUID.randomUUID();
        MatchingService.MatchingResult expected = new MatchingService.MatchingResult();
        when(emptyRouteService.findOptimalMatching(List.of(), carrierId.toString())).thenReturn(expected);

        var response = controller.findOptimalMatching(carrierId, "CARRIER", List.of());

        verify(identityVerificationClient).assertVerified(carrierId);
        verify(emptyRouteService).findOptimalMatching(List.of(), carrierId.toString());
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void adminOptimalMatchingMatchesAcrossAllCompaniesWithoutCarrierVerification() {
        EmptyRouteController controller = new EmptyRouteController(
                emptyRouteService, identityVerificationClient, emptyRouteMapper);
        UUID adminId = UUID.randomUUID();
        MatchingService.MatchingResult expected = new MatchingService.MatchingResult();
        when(emptyRouteService.findOptimalMatching(List.of(), null)).thenReturn(expected);

        var response = controller.findOptimalMatching(adminId, "ADMIN", List.of());

        verifyNoInteractions(identityVerificationClient);
        verify(emptyRouteService).findOptimalMatching(List.of(), null);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void internalEmptyRouteControllerRequiresValidConstantTimeToken() {
        InternalEmptyRouteController controller = new InternalEmptyRouteController(emptyRouteService, emptyRouteMapper);
        ReflectionTestUtils.setField(controller, "expectedToken", "secret-internal-token");

        assertThatThrownBy(() -> controller.findMatchingRoutes(null, Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> controller.findOptimalMatching("wrong-token", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        when(emptyRouteService.findOptimalMatching(List.of())).thenReturn(new MatchingService.MatchingResult());
        assertThat(controller.findOptimalMatching("secret-internal-token", List.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void emptyRouteControllersReturnMappedResponseDtos() {
        UUID routeId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        LocalDateTime emptyAt = LocalDateTime.of(2026, 11, 20, 9, 0);
        EmptyRoute entity = EmptyRoute.builder()
                .id(routeId)
                .truckId("51C-123.45")
                .truckType("TRUCK_HEAVY")
                .companyId(carrierId.toString())
                .availableCapacity(10.0)
                .origin("TP.HCM")
                .latitude(10.7769)
                .longitude(106.7009)
                .destination("Hà Nội")
                .destLatitude(21.0278)
                .destLongitude(105.8342)
                .expectedEmptyTime(emptyAt)
                .searchRadius(50)
                .status(EmptyRouteStatus.PENDING)
                .build();

        when(emptyRouteService.listMine(carrierId.toString())).thenReturn(List.of(entity));
        EmptyRouteController controller = new EmptyRouteController(
                emptyRouteService, identityVerificationClient, emptyRouteMapper);

        List<EmptyRouteResponse> responses = controller.listMine(carrierId, "CARRIER");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(routeId);
        assertThat(responses.get(0).truckId()).isEqualTo("51C-123.45");
        assertThat(responses.get(0).status()).isEqualTo(EmptyRouteStatus.PENDING);
    }

    @Test
    void parseLatestPickupUsesVietnamTimezone() {
        assertThat(EmptyRouteService.parseLatestPickup(Map.of("latestPickup", "2026-11-18T10:00:00Z")))
                .contains(LocalDateTime.of(2026, 11, 18, 17, 0, 0));
    }
}
