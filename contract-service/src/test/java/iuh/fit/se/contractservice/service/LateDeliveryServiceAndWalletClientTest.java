package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.entity.Trip;
import iuh.fit.se.contractservice.domain.entity.TripDelaySettlement;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.repository.TripDelaySettlementRepository;
import iuh.fit.se.contractservice.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LateDeliveryServiceAndWalletClientTest {

    @Test
    void walletClientSendsInternalWalletTokenHeader() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        LateDeliveryWalletClient client = new LateDeliveryWalletClient(
                restTemplate, "http://wallet-service:8080", "wallet-secret-token");

        UUID shipperId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        server.expect(requestTo("http://wallet-service:8080/internal/wallet-holds/hold-1/settlements"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "wallet-secret-token"))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

        client.settle("hold-1", shipperId, new BigDecimal("500000"), tripId, 1);
        server.verify();
    }

    @Test
    void processTripDoesNotDeductReputationOrNotifyWhenWalletSettleFailsWith401() {
        TripRepository tripRepository = mock(TripRepository.class);
        TripDelaySettlementRepository settlementRepository = mock(TripDelaySettlementRepository.class);
        LateDeliveryWalletClient walletClient = mock(LateDeliveryWalletClient.class);
        CarrierReputationClient reputationClient = mock(CarrierReputationClient.class);
        LateDeliveryNotificationClient notificationClient = mock(LateDeliveryNotificationClient.class);

        LateDeliveryService service = new LateDeliveryService(
                tripRepository, settlementRepository, walletClient, reputationClient, notificationClient);

        UUID tripId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Trip trip = Trip.builder()
                .id(tripId)
                .shipperId(shipperId)
                .carrierId(carrierId)
                .depositHoldId("hold-401")
                .depositAmount(new BigDecimal("1000000"))
                .expectedDeliveryAt(Instant.now().minus(Duration.ofMinutes(20)))
                .status(TripStatus.IN_TRANSIT)
                .build();

        when(settlementRepository.findByTripIdAndTier(tripId, 1)).thenReturn(Optional.empty());
        when(settlementRepository.saveAndFlush(any(TripDelaySettlement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(TripDelaySettlement.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RestClientResponseException("Unauthorized", HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                null, new byte[0], StandardCharsets.UTF_8))
                .when(walletClient).settle(anyString(), any(), any(), any(), anyInt());

        service.processTrip(trip);

        verify(reputationClient, never()).deductLateDeliveryPoints(any(), any(), anyInt());
        verify(notificationClient, never()).notify(any(), any(), anyInt(), anyLong(), anyString());
    }

    @Test
    void processTripCompletesReputationAndNotificationsWhenWalletSucceeds() {
        TripRepository tripRepository = mock(TripRepository.class);
        TripDelaySettlementRepository settlementRepository = mock(TripDelaySettlementRepository.class);
        LateDeliveryWalletClient walletClient = mock(LateDeliveryWalletClient.class);
        CarrierReputationClient reputationClient = mock(CarrierReputationClient.class);
        LateDeliveryNotificationClient notificationClient = mock(LateDeliveryNotificationClient.class);

        LateDeliveryService service = new LateDeliveryService(
                tripRepository, settlementRepository, walletClient, reputationClient, notificationClient);

        UUID tripId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Trip trip = Trip.builder()
                .id(tripId)
                .shipperId(shipperId)
                .carrierId(carrierId)
                .depositHoldId("hold-ok")
                .depositAmount(new BigDecimal("1000000"))
                .expectedDeliveryAt(Instant.now().minus(Duration.ofMinutes(20)))
                .status(TripStatus.IN_TRANSIT)
                .build();

        when(settlementRepository.findByTripIdAndTier(tripId, 1)).thenReturn(Optional.empty());
        when(settlementRepository.saveAndFlush(any(TripDelaySettlement.class))).thenAnswer(inv -> inv.getArgument(0));
        TripDelaySettlement[] saved = new TripDelaySettlement[1];
        when(settlementRepository.save(any(TripDelaySettlement.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return saved[0];
        });

        service.processTrip(trip);

        verify(walletClient).settle("hold-ok", shipperId, new BigDecimal("50000.00"), tripId, 1);
        verify(reputationClient).deductLateDeliveryPoints(carrierId, tripId, 1);
        verify(notificationClient).notify(shipperId, tripId, 1, 20L, "SHIPPER");
        verify(notificationClient).notify(carrierId, tripId, 1, 20L, "CARRIER");
        assertThat(saved[0].getOverallStatus()).isEqualTo("COMPLETED");
    }
}
