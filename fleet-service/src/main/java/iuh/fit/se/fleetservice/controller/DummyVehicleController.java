package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.Vehicle;
import iuh.fit.se.fleetservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class DummyVehicleController {

    private final VehicleRepository vehicleRepository;

    @GetMapping("/mine")
    public ResponseEntity<?> getMyVehicles(@RequestHeader("x-user-id") String userId) {
        UUID carrierId = UUID.fromString(userId);
        List<Vehicle> vehicles = vehicleRepository.findByCarrierIdOrderByLicensePlateAsc(carrierId);
        
        List<Map<String, Object>> data = vehicles.stream().map(v -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", v.getId());
            map.put("carrierId", v.getCarrierId());
            map.put("licensePlate", v.getLicensePlate());
            map.put("vehicleType", v.getVehicleType().name());
            map.put("bodyType", v.getBodyType() != null ? v.getBodyType() : "");
            map.put("payloadCapacity", v.getPayloadCapacity());
            map.put("status", v.getStatus().name());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", data, "total", data.size(), "page", 1, "pageSize", 100));
    }
}
