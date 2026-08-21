package iuh.fit.se.fleetservice.controller;

import iuh.fit.se.fleetservice.domain.entity.DriverProfile;
import iuh.fit.se.fleetservice.repository.DriverProfileRepository;
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
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DummyDriverController {

    private final DriverProfileRepository driverProfileRepository;

    @GetMapping("/mine")
    public ResponseEntity<?> getMyDrivers(@RequestHeader("x-user-id") String userId) {
        UUID carrierId = UUID.fromString(userId);
        List<DriverProfile> drivers = driverProfileRepository.findByCarrierIdOrderByFullNameAsc(carrierId);
        
        List<Map<String, Object>> data = drivers.stream().map(d -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", d.getId());
            map.put("carrierId", d.getCarrierId());
            map.put("fullName", d.getFullName());
            map.put("phone", d.getPhone());
            map.put("licenseNumber", d.getLicenseNumber());
            map.put("status", d.getStatus().name());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", data, "total", data.size(), "page", 1, "pageSize", 100));
    }
}
