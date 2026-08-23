package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.dto.CreateDriverRequest;
import iuh.fit.se.fleetservice.dto.DriverResponse;
import iuh.fit.se.fleetservice.dto.UpdateDriverRequest;
import iuh.fit.se.fleetservice.dto.PublicDriverResponse;
import iuh.fit.se.fleetservice.service.DriverProfileService;
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
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {
    private final DriverProfileService driverProfileService;

    @GetMapping("/mine")
    public List<DriverResponse> getMyDrivers(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) VerificationStatus status
    ) {
        requireCarrierOrAdmin(role);
        return driverProfileService.listMine(carrierId, status).stream()
                .map(DriverResponse::from)
                .toList();
    }

    @GetMapping("/carrier/{carrierId}")
    public List<PublicDriverResponse> getCarrierDrivers(
            @PathVariable UUID carrierId,
            @RequestHeader("X-User-Role") String role
    ) {
        requireShipperCarrierOrAdmin(role);
        return driverProfileService.listMine(carrierId, null).stream()
                .map(PublicDriverResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<DriverResponse> create(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateDriverRequest request
    ) {
        requireCarrier(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DriverResponse.from(driverProfileService.create(carrierId, request)));
    }



    @PostMapping("/bulk")
    public ResponseEntity<List<DriverResponse>> createBulk(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @RequestBody List<@Valid CreateDriverRequest> requests
    ) {
        requireCarrier(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverProfileService.createBulk(carrierId, requests).stream().map(DriverResponse::from).toList());
    }



    @PatchMapping("/{driverId}")
    public DriverResponse update(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateDriverRequest request
    ) {
        requireCarrier(role);
        return DriverResponse.from(driverProfileService.update(carrierId, driverId, request));
    }



    @DeleteMapping("/{driverId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") UUID carrierId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID driverId
    ) {
        requireCarrier(role);
        driverProfileService.delete(carrierId, driverId);
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
