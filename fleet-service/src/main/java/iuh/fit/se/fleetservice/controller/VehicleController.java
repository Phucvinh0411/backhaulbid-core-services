package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.dto.CreateVehicleRequest;
import iuh.fit.se.fleetservice.dto.UpdateVehicleRequest;
import iuh.fit.se.fleetservice.dto.VehicleResponse;
import iuh.fit.se.fleetservice.dto.PublicVehicleResponse;
import iuh.fit.se.fleetservice.dto.ZipImportPreviewResponse;
import iuh.fit.se.fleetservice.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService vehicleService;

    @GetMapping("/mine")
    public List<VehicleResponse> getMyVehicles(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) VehicleStatus status
    ) {
        requireCarrierOrAdmin(role);
        return vehicleService.listMine(carrierId, status)
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @GetMapping("/carrier/{carrierId}")
    public List<PublicVehicleResponse> getCarrierVehicles(
            @PathVariable UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        requireShipperCarrierOrAdmin(role);
        return vehicleService.listMine(carrierId, null).stream()
                .map(PublicVehicleResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        requireCarrier(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(VehicleResponse.from(vehicleService.create(carrierId, request)));
    }



    @PostMapping("/bulk")
    public ResponseEntity<List<VehicleResponse>> createBulk(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @RequestBody List<@Valid CreateVehicleRequest> requests
    ) {
        requireCarrier(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.createBulk(carrierId, requests).stream().map(VehicleResponse::from).toList());
    }


    @PatchMapping("/{vehicleId}")
    public VehicleResponse update(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        requireCarrier(role);
        return VehicleResponse.from(vehicleService.update(carrierId, vehicleId, request));
    }



    @DeleteMapping("/{vehicleId}")
    public ResponseEntity<Void> deactivate(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID vehicleId
    ) {
        requireCarrier(role);
        vehicleService.deactivate(carrierId, vehicleId);
        return ResponseEntity.noContent().build();
    }

    private void requireCarrier(String role) {
        if (!"CARRIER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier role required");
        }
    }

    private void requireCarrierOrAdmin(String role) {
        if (!"CARRIER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Carrier or admin role required");
        }
    }

    private void requireShipperCarrierOrAdmin(String role) {
        if (!"SHIPPER".equalsIgnoreCase(role)
                && !"CARRIER".equalsIgnoreCase(role)
                && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role is not allowed");
        }
    }
}
