package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.dto.AddComplaintMessageRequest;
import iuh.fit.se.contractservice.dto.ComplaintResponse;
import iuh.fit.se.contractservice.dto.CreateComplaintRequest;
import iuh.fit.se.contractservice.dto.ResolveComplaintRequest;
import iuh.fit.se.contractservice.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintController {
    private final ComplaintService complaintService;

    @GetMapping
    public List<ComplaintResponse> list(@RequestHeader("X-User-Id") UUID accountId, @RequestHeader("X-User-Role") String role) {
        return complaintService.list(accountId, parseRole(role));
    }

    @GetMapping("/{complaintId}")
    public ComplaintResponse get(@RequestHeader("X-User-Id") UUID accountId, @RequestHeader("X-User-Role") String role, @PathVariable UUID complaintId) {
        return complaintService.get(accountId, parseRole(role), complaintId);
    }

    @GetMapping(value = "/{complaintId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader("X-User-Id") UUID accountId, @RequestHeader("X-User-Role") String role, @PathVariable UUID complaintId) {
        return complaintService.subscribe(accountId, parseRole(role), complaintId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintResponse create(@RequestHeader("X-User-Id") UUID accountId, @RequestHeader("X-User-Role") String role, @Valid @RequestBody CreateComplaintRequest request) {
        return complaintService.create(accountId, parseRole(role), request);
    }

    @PostMapping("/{complaintId}/messages")
    public ComplaintResponse addMessage(@RequestHeader("X-User-Id") UUID accountId, @RequestHeader("X-User-Role") String role, @PathVariable UUID complaintId, @Valid @RequestBody AddComplaintMessageRequest request) {
        return complaintService.addMessage(accountId, parseRole(role), complaintId, request);
    }

    @PatchMapping("/{complaintId}/decision")
    public ComplaintResponse resolve(@RequestHeader("X-User-Id") UUID accountId, @RequestHeader("X-User-Role") String role, @PathVariable UUID complaintId, @Valid @RequestBody ResolveComplaintRequest request) {
        return complaintService.resolve(accountId, parseRole(role), complaintId, request);
    }

    private AccountRole parseRole(String role) {
        try {
            return AccountRole.valueOf(role.toUpperCase());
        } catch (Exception exception) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported account role");
        }
    }
}
