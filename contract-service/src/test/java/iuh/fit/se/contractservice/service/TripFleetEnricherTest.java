package iuh.fit.se.contractservice.service;

import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.ContractResponse;
import iuh.fit.se.contractservice.dto.TripResponse;
import iuh.fit.se.contractservice.domain.enums.ContractStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TripFleetEnricherTest {

    private TripResponse sampleTrip(UUID tripId, UUID shipperId, UUID carrierId, UUID vehicleId, UUID driverId) {
        return new TripResponse(
                tripId, shipperId, carrierId, vehicleId, driverId, null, false,
                "auc-1", "bid-1", "HCM", "HN", new BigDecimal("10000000"),
                Instant.now().plusSeconds(3600), null, null, null, false, 0, false,
                TripStatus.IN_TRANSIT, null, Instant.now(), Instant.now(),
                null, null, null, 1L, true, 1L, null, null);
    }

    @Test
    void batchesFleetLookupsPerCarrierAndEnrichesContractsAndTrips() {
        TripFleetEnricher enricher = new TripFleetEnricher(new RestTemplateBuilder(), "http://fleet-service:8080");
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(enricher, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID trip1Id = UUID.randomUUID();
        UUID trip2Id = UUID.randomUUID();

        server.expect(requestTo("http://fleet-service:8080/api/v1/drivers/carrier/" + carrierId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + driverId + "\",\"fullName\":\"Nguyễn Văn Hậu\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://fleet-service:8080/api/v1/vehicles/carrier/" + carrierId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + vehicleId + "\",\"licensePlate\":\"51C-468.25\"}]", MediaType.APPLICATION_JSON));

        ContractResponse c1 = new ContractResponse(UUID.randomUUID(), trip1Id, shipperId, carrierId, "AUC-1",
                null, null, ContractStatus.SIGNED, null, Instant.now(), Instant.now(),
                sampleTrip(trip1Id, shipperId, carrierId, vehicleId, driverId), List.of());
        ContractResponse c2 = new ContractResponse(UUID.randomUUID(), trip2Id, shipperId, carrierId, "AUC-2",
                null, null, ContractStatus.SIGNED, null, Instant.now(), Instant.now(),
                sampleTrip(trip2Id, shipperId, carrierId, vehicleId, driverId), List.of());

        List<ContractResponse> enriched = enricher.enrichContracts(List.of(c1, c2));
        assertThat(enriched).hasSize(2);
        assertThat(enriched.get(0).trip().driverName()).isEqualTo("Nguyễn Văn Hậu");
        assertThat(enriched.get(0).trip().vehiclePlate()).isEqualTo("51C-468.25");
        assertThat(enriched.get(1).trip().driverName()).isEqualTo("Nguyễn Văn Hậu");
        server.verify();
    }

    @Test
    void returnsOriginalTripsGracefullyWhenFleetServiceFails() {
        TripFleetEnricher enricher = new TripFleetEnricher(new RestTemplateBuilder(), "http://fleet-service:8080");
        RestTemplate rt = (RestTemplate) ReflectionTestUtils.getField(enricher, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        UUID carrierId = UUID.randomUUID();
        server.expect(requestTo("http://fleet-service:8080/api/v1/drivers/carrier/" + carrierId))
                .andRespond(withServerError());
        server.expect(requestTo("http://fleet-service:8080/api/v1/vehicles/carrier/" + carrierId))
                .andRespond(withServerError());

        TripResponse trip = sampleTrip(UUID.randomUUID(), UUID.randomUUID(), carrierId, UUID.randomUUID(), UUID.randomUUID());
        TripResponse result = enricher.enrichTrip(trip);
        assertThat(result.driverName()).isNull();
        assertThat(result.vehiclePlate()).isNull();
        server.verify();
    }
}
