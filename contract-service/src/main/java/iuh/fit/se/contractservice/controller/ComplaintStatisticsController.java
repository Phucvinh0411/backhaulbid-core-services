package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.ComplaintStatus;
import iuh.fit.se.contractservice.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/complaints/statistics")
@RequiredArgsConstructor
public class ComplaintStatisticsController {
    private final ComplaintRepository complaints;

    @GetMapping("/admin")
    public Map<String, Object> admin(@RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required");
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long open = 0;
        for (ComplaintStatus status : ComplaintStatus.values()) {
            long count = complaints.countByStatus(status);
            byStatus.put(status.name(), count);
            if (status == ComplaintStatus.PENDING || status == ComplaintStatus.PROCESSING) open += count;
        }
        return Map.of("complaintsByStatus", byStatus, "openCount", open, "refreshedAt", java.time.Instant.now().toString());
    }
}
