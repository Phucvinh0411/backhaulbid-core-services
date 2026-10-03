package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.domain.enums.TripStatus;
import iuh.fit.se.contractservice.dto.CreateTrackingRequest;
import iuh.fit.se.contractservice.dto.AssignDriverRequest;
import iuh.fit.se.contractservice.dto.TrackingResponse;
import iuh.fit.se.contractservice.dto.TripAssignmentResponse;
import iuh.fit.se.contractservice.dto.TripResponse;
import iuh.fit.se.contractservice.dto.UpdateTripStatusRequest;
import iuh.fit.se.contractservice.dto.CreateJourneyEventRequest;
import iuh.fit.se.contractservice.dto.JourneyEventResponse;
import iuh.fit.se.contractservice.dto.CreateDeliveryProofRequest;
import iuh.fit.se.contractservice.dto.DeliveryProofResponse;
import iuh.fit.se.contractservice.dto.CreateTripLocationRequest;
import iuh.fit.se.contractservice.dto.TripLocationResponse;
import iuh.fit.se.contractservice.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;

    @GetMapping("/mine")
    public List<TripResponse> listMine(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) TripStatus status
    ) {
        return tripService.list(accountId, parseRole(role), status).stream().map(TripResponse::from).toList();
    }

    @GetMapping("/{tripId}")
    public TripResponse get(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId
    ) {
        return TripResponse.from(tripService.get(accountId, parseRole(role), tripId));
    }

    @PatchMapping("/{tripId}/status")
    public TripResponse updateStatus(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateTripStatusRequest request
    ) {
        return TripResponse.from(tripService.updateStatus(accountId, parseRole(role), tripId, request));
    }

    @PatchMapping("/{tripId}/assignment")
    public TripAssignmentResponse assignDriver(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody AssignDriverRequest request
    ) {
        return tripService.assignDriver(accountId, parseRole(role), tripId, request);
    }

    @PostMapping("/{tripId}/tracking")
    public TrackingResponse addTracking(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateTrackingRequest request
    ) {
        return TrackingResponse.from(tripService.addTracking(accountId, parseRole(role), tripId, request));
    }

    @PostMapping("/{tripId}/journey-events")
    public JourneyEventResponse addJourneyEvent(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateJourneyEventRequest request
    ) {
        return JourneyEventResponse.from(tripService.addJourneyEvent(accountId, parseRole(role), tripId, request));
    }

    @GetMapping("/{tripId}/journey-events")
    public List<JourneyEventResponse> journey(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId
    ) {
        return tripService.journey(accountId, parseRole(role), tripId).stream()
                .map(JourneyEventResponse::from).toList();
    }

    @PostMapping("/{tripId}/delivery-proofs")
    public DeliveryProofResponse addProof(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateDeliveryProofRequest request
    ) {
        return DeliveryProofResponse.from(tripService.addDeliveryProof(accountId, parseRole(role), tripId, request));
    }

    @GetMapping("/{tripId}/delivery-proofs")
    public List<DeliveryProofResponse> proofs(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId
    ) {
        return tripService.proofs(accountId, parseRole(role), tripId).stream()
                .map(DeliveryProofResponse::from).toList();
    }

    @PostMapping("/{tripId}/locations")
    public TripLocationResponse addLocation(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateTripLocationRequest request
    ) {
        return TripLocationResponse.from(tripService.addLocation(accountId, parseRole(role), tripId, request));
    }

    @GetMapping("/{tripId}/locations")
    public List<TripLocationResponse> locations(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId
    ) {
        return tripService.locations(accountId, parseRole(role), tripId).stream()
                .map(TripLocationResponse::from).toList();
    }

    @GetMapping("/{tripId}/locations/latest")
    public TripLocationResponse latestLocation(
            @RequestHeader("X-User-Id") UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId
    ) {
        var latest = tripService.latestLocation(accountId, parseRole(role), tripId);
        return latest == null ? null : TripLocationResponse.from(latest);
    }

    private AccountRole parseRole(String role) {
        try {
            return AccountRole.valueOf(role.toUpperCase());
        } catch (Exception exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Unsupported account role");
        }
    }
}
