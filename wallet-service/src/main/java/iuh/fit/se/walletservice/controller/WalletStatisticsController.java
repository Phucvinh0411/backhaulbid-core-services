package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.statistics.WalletStatisticsRange;
import iuh.fit.se.walletservice.statistics.WalletStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallets/statistics")
@RequiredArgsConstructor
public class WalletStatisticsController {
    private final WalletStatisticsService statistics;

    @GetMapping("/admin")
    public Map<String, Object> admin(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String bucket
    ) {
        return statistics.admin(role, WalletStatisticsRange.parse(dateFrom, dateTo, bucket));
    }
}
