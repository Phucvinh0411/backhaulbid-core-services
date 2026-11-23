package iuh.fit.se.contractservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import iuh.fit.se.contractservice.dto.ContractResponse;
import iuh.fit.se.contractservice.dto.TripResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Enriches trip responses with vehicle license plate and assigned driver name via batched fleet-service lookups. */
@Component
public class TripFleetEnricher {
    private final RestTemplate restTemplate;
    private final String fleetServiceUrl;

    public TripFleetEnricher(RestTemplateBuilder builder,
            @Value("${FLEET_SERVICE_URL:http://fleet-service:8080}") String fleetServiceUrl) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3)).build();
        this.fleetServiceUrl = fleetServiceUrl.replaceAll("/+$", "");
    }

    public TripResponse enrichTrip(TripResponse trip) {
        if (trip == null) return null;
        return enrichTrips(List.of(trip)).get(0);
    }

    public List<TripResponse> enrichTrips(List<TripResponse> trips) {
        if (trips == null || trips.isEmpty()) return trips;
        Map<UUID, String> driverNames = new HashMap<>();
        Map<UUID, String> vehiclePlates = new HashMap<>();
        trips.stream()
                .filter(Objects::nonNull)
                .filter(t -> t.carrierId() != null && (t.driverId() != null || t.vehicleId() != null))
                .map(TripResponse::carrierId)
                .distinct()
                .forEach(carrierId -> loadCarrierFleet(carrierId, driverNames, vehiclePlates));
        return trips.stream()
                .map(trip -> trip == null ? null : copyWithFleetInfo(
                        trip,
                        trip.driverId() == null ? null : driverNames.get(trip.driverId()),
                        trip.vehicleId() == null ? null : vehiclePlates.get(trip.vehicleId())))
                .toList();
    }

    public ContractResponse enrichContract(ContractResponse contract) {
        if (contract == null || contract.trip() == null) return contract;
        return enrichContracts(List.of(contract)).get(0);
    }

    public List<ContractResponse> enrichContracts(List<ContractResponse> contracts) {
        if (contracts == null || contracts.isEmpty()) return contracts;
        List<TripResponse> trips = contracts.stream().map(ContractResponse::trip).filter(Objects::nonNull).toList();
        if (trips.isEmpty()) return contracts;
        Map<UUID, TripResponse> enrichedById = new HashMap<>();
        enrichTrips(trips).forEach(t -> enrichedById.put(t.id(), t));
        return contracts.stream()
                .map(contract -> contract.trip() == null ? contract
                        : copyWithTrip(contract, enrichedById.getOrDefault(contract.trip().id(), contract.trip())))
                .toList();
    }

    private static TripResponse copyWithFleetInfo(TripResponse trip, String driverName, String vehiclePlate) {
        return new TripResponse(
                trip.id(), trip.shipperId(), trip.carrierId(), trip.vehicleId(), trip.driverId(),
                trip.driverAccountId(), trip.hasAssignmentPin(), trip.auctionId(), trip.winningBidId(),
                trip.pickupLocation(), trip.deliveryLocation(), trip.agreedPrice(), trip.expectedDeliveryAt(),
                trip.deliveredAt(), trip.depositAmount(), trip.depositReleasedAt(), trip.latePolicyEnabled(),
                trip.lateMinutes(), trip.canCancelForLateDelivery(), trip.status(), trip.cancellationReason(),
                trip.createdAt(), trip.updatedAt(), trip.latestTracking(), trip.pickupPoint(), trip.deliveryPoint(),
                trip.routeVersion(), trip.driverConnected(), trip.assignmentVersion(), driverName, vehiclePlate);
    }

    private static ContractResponse copyWithTrip(ContractResponse contract, TripResponse trip) {
        return new ContractResponse(
                contract.id(), contract.tripId(), contract.shipperId(), contract.carrierId(),
                contract.contractCode(), contract.pdfUrl(), contract.pdfHashSha256(), contract.status(),
                contract.signingDeadlineAt(), contract.createdAt(), contract.updatedAt(), trip, contract.signatures());
    }

    private void loadCarrierFleet(UUID carrierId, Map<UUID, String> driverNames, Map<UUID, String> vehiclePlates) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "CARRIER");
        headers.set("X-User-Id", carrierId.toString());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            var driversUri = UriComponentsBuilder.fromHttpUrl(fleetServiceUrl)
                    .path("/api/v1/drivers/carrier/{carrierId}").buildAndExpand(carrierId).toUri();
            DriverItem[] drivers = restTemplate.exchange(driversUri, HttpMethod.GET, entity, DriverItem[].class).getBody();
            if (drivers != null) {
                for (DriverItem driver : drivers) {
                    if (driver != null && driver.id() != null && driver.fullName() != null) {
                        driverNames.putIfAbsent(driver.id(), driver.fullName());
                    }
                }
            }
        } catch (RestClientException ignored) {
            // Best-effort UI enrichment; trip responses remain usable if fleet-service is unavailable.
        }
        try {
            var vehiclesUri = UriComponentsBuilder.fromHttpUrl(fleetServiceUrl)
                    .path("/api/v1/vehicles/carrier/{carrierId}").buildAndExpand(carrierId).toUri();
            VehicleItem[] vehicles = restTemplate.exchange(vehiclesUri, HttpMethod.GET, entity, VehicleItem[].class).getBody();
            if (vehicles != null) {
                for (VehicleItem vehicle : vehicles) {
                    if (vehicle != null && vehicle.id() != null && vehicle.licensePlate() != null) {
                        vehiclePlates.putIfAbsent(vehicle.id(), vehicle.licensePlate());
                    }
                }
            }
        } catch (RestClientException ignored) {
            // Best-effort UI enrichment; trip responses remain usable if fleet-service is unavailable.
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DriverItem(UUID id, String fullName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VehicleItem(UUID id, String licensePlate) {}
}
