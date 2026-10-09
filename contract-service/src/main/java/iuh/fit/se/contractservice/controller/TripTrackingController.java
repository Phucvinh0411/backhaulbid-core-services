package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.dto.GpsBatchRequest;
import iuh.fit.se.contractservice.service.TripTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/trips") @RequiredArgsConstructor
public class TripTrackingController {
    private final TripTrackingService tracking;
    @PostMapping("/{id}/tracking-sessions")
    public TripTrackingService.Session start(@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role,@PathVariable UUID id) {
        return tracking.start(actor,role(role),id);
    }
    @PostMapping("/{id}/tracking-sessions/{sessionId}/stop")
    public TripTrackingService.Session stop(@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role,@PathVariable UUID id,@PathVariable UUID sessionId) {
        return tracking.stop(actor,role(role),id,sessionId);
    }
    @PostMapping("/{id}/locations/batch")
    public TripTrackingService.Batch upload(@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role,@PathVariable UUID id,@Valid @RequestBody GpsBatchRequest request) {
        return tracking.upload(actor,role(role),id,request);
    }
    @GetMapping("/{id}/tracking")
    public ResponseEntity<TripTrackingService.Snapshot> snapshot(@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tracking.snapshot(actor,role(role),id));
    }
    @GetMapping("/{id}/locations/history")
    public ResponseEntity<TripTrackingService.History> history(@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role,@PathVariable UUID id,
            @RequestParam(defaultValue="1000") int limit,@RequestParam(required=false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tracking.history(actor,role(role),id,limit,cursor));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> concurrentStart(){return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message","Tracking changed concurrently; refresh and retry"));}
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> businessError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of("message",
                java.util.Objects.requireNonNullElse(error.getReason(),"Tracking request could not be completed")));
    }
    private AccountRole role(String raw) {
        try{return AccountRole.valueOf(raw.toUpperCase(java.util.Locale.ROOT));}
        catch(Exception invalid){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Unsupported account role");}
    }
}
