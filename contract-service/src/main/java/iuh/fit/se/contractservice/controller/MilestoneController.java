package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.domain.enums.AccountRole;
import iuh.fit.se.contractservice.dto.CreateMilestoneRequest;
import iuh.fit.se.contractservice.dto.MilestoneCheckInRequest;
import iuh.fit.se.contractservice.dto.MilestoneResponse;
import iuh.fit.se.contractservice.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    /** GET /api/v1/trips/{tripId}/milestones */
    @GetMapping
    public List<MilestoneResponse> list(
            @RequestHeader("X-User-Id")   UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId
    ) {
        return milestoneService.listMilestones(accountId, parseRole(role), tripId)
                .stream().map(MilestoneResponse::from).toList();
    }

    /** POST /api/v1/trips/{tripId}/milestones */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MilestoneResponse create(
            @RequestHeader("X-User-Id")   UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateMilestoneRequest request
    ) {
        return MilestoneResponse.from(
                milestoneService.createMilestone(accountId, parseRole(role), tripId, request));
    }

    /**
     * POST /api/v1/trips/{tripId}/milestones/{milestoneId}/checkin
     *
     * <p>Body: {@code { "currentLat": 10.762622, "currentLng": 106.660172 }}
     * <p>Tài xế gọi API này khi đến cột mốc. Backend kiểm tra geo-fence (Haversine ≤ 2 km).
     */
    @PostMapping("/{milestoneId}/checkin")
    public MilestoneResponse checkIn(
            @RequestHeader("X-User-Id")   UUID accountId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID tripId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody MilestoneCheckInRequest request
    ) {
        return MilestoneResponse.from(
                milestoneService.checkIn(accountId, parseRole(role), tripId, milestoneId, request));
    }

    private AccountRole parseRole(String role) {
        try {
            return AccountRole.valueOf(role.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported account role");
        }
    }
}
