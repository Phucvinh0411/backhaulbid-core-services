package iuh.fit.se.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts/statistics")
@RequiredArgsConstructor
public class AccountStatisticsController {
    private final AccountStatisticsService statistics;

    @GetMapping("/admin")
    public Map<String, Object> admin(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String bucket
    ) {
        if (!role.equalsIgnoreCase("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required");
        return statistics.admin(AccountStatisticsRange.parse(dateFrom, dateTo, bucket));
    }
}
