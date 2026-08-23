package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.enums.VehicleStatus;
import iuh.fit.se.fleetservice.domain.enums.VerificationStatus;
import iuh.fit.se.fleetservice.dto.DriverResponse;
import iuh.fit.se.fleetservice.dto.ReviewVerificationRequest;
import iuh.fit.se.fleetservice.dto.VehicleResponse;
import iuh.fit.se.fleetservice.service.DriverProfileService;
import iuh.fit.se.fleetservice.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/fleet")
@RequiredArgsConstructor
public class AdminFleetController {
    private final VehicleService vehicleService;
    private final DriverProfileService driverProfileService;

    @GetMapping("/vehicles")
    public List<VehicleResponse> listVehicles(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "PENDING") VehicleStatus status
    ) {
        requireAdmin(role);
        return vehicleService.listForReview(status).stream().map(VehicleResponse::from).toList();
    }

    @PatchMapping("/vehicles/{vehicleId}/verification")
    public VehicleResponse reviewVehicle(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody ReviewVerificationRequest request
    ) {
        requireAdmin(role);
        return VehicleResponse.from(vehicleService.review(adminId, vehicleId, request));
    }

    @GetMapping("/drivers")
    public List<DriverResponse> listDrivers(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "PENDING") VerificationStatus status
    ) {
        requireAdmin(role);
        return driverProfileService.listForReview(status).stream().map(DriverResponse::from).toList();
    }

    @PatchMapping("/drivers/{driverId}/verification")
    public DriverResponse reviewDriver(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID driverId,
            @Valid @RequestBody ReviewVerificationRequest request
    ) {
        requireAdmin(role);
        return DriverResponse.from(driverProfileService.review(adminId, driverId, request));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}
