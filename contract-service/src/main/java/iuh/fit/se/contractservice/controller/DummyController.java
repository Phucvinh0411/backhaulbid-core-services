package iuh.fit.se.contractservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DummyController {

    @GetMapping("/contracts/mine")
    public ResponseEntity<?> getMyContracts() {
        return ResponseEntity.ok(Map.of("data", Collections.emptyList(), "total", 0, "page", 1, "pageSize", 100));
    }

    @GetMapping("/trips/mine")
    public ResponseEntity<?> getMyTrips() {
        return ResponseEntity.ok(Map.of("data", Collections.emptyList(), "total", 0, "page", 1, "pageSize", 100));
    }
}
