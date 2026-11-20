package iuh.fit.se.fleetservice.service;

import iuh.fit.se.fleetservice.controller.EmptyRouteController;
import iuh.fit.se.fleetservice.controller.InternalEmptyRouteController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void carrierOptimalMatchingRequiresVerificationAndScopesToCallerCompany() {
        EmptyRouteController controller = new EmptyRouteController(emptyRouteService, identityVerificationClient);
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
        EmptyRouteController controller = new EmptyRouteController(emptyRouteService, identityVerificationClient);
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
        InternalEmptyRouteController controller = new InternalEmptyRouteController(emptyRouteService);
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
    void parseLatestPickupUsesVietnamTimezone() {
        assertThat(EmptyRouteService.parseLatestPickup(Map.of("latestPickup", "2026-11-18T10:00:00Z")))
                .contains(LocalDateTime.of(2026, 11, 18, 17, 0, 0));
    }
}
