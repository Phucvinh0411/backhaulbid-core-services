package iuh.fit.se.contractservice.controller;

import iuh.fit.se.contractservice.dto.AuctionAwardResponse;
import iuh.fit.se.contractservice.dto.AuctionAwardAttemptStatusResponse;
import iuh.fit.se.contractservice.dto.CreateAuctionAwardRequest;
import iuh.fit.se.contractservice.service.AwardedContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/auction-awards")
@RequiredArgsConstructor
public class InternalAuctionAwardController {
    private final AwardedContractService awardedContractService;

    @Value("${INTERNAL_SERVICE_TOKEN:}")
    private String expectedToken;

    @PostMapping
    public AuctionAwardResponse create(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody CreateAuctionAwardRequest request
    ) {
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
        return awardedContractService.createOrGet(request);
    }

    @GetMapping("/{awardAttemptId}")
    public AuctionAwardAttemptStatusResponse status(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable String awardAttemptId) {
        authorize(token);
        return awardedContractService.status(awardAttemptId);
    }

    @PostMapping("/{awardAttemptId}/expire")
    public AuctionAwardAttemptStatusResponse expireIfDeadlinePassed(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable String awardAttemptId) {
        authorize(token);
        return awardedContractService.expireIfDeadlinePassed(awardAttemptId);
    }

    private void authorize(String token) {
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
    }
}
