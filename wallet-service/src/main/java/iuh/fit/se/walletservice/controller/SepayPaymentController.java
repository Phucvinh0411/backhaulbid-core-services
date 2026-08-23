package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.request.SepayIpnRequest;
import iuh.fit.se.walletservice.dto.request.TopUpRequest;
import iuh.fit.se.walletservice.dto.response.TopUpResponse;
import iuh.fit.se.walletservice.dto.response.TopUpStatusResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderPageResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import iuh.fit.se.walletservice.service.PaymentOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Separates browser checkout creation from the provider-to-server IPN endpoint. */
@RestController
@RequestMapping("/api/v1/payments/sepay")
public class SepayPaymentController {
    private final PaymentOrderService paymentOrderService;

    public SepayPaymentController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @PostMapping("/top-ups")
    public TopUpResponse createTopUp(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody TopUpRequest request) {
        return paymentOrderService.createTopUp(accountId(userId), request);
    }

    @GetMapping("/top-ups/{invoiceNumber}")
    public TopUpStatusResponse topUpStatus(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String invoiceNumber) {
        return paymentOrderService.getTopUpStatus(accountId(userId), invoiceNumber);
    }

    @GetMapping("/top-ups")
    public PaymentOrderPageResponse listTopUps(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return paymentOrderService.listMine(accountId(userId), page, pageSize);
    }

    @PostMapping("/top-ups/{invoiceNumber}/cancel")
    public PaymentOrderResponse cancelTopUp(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String invoiceNumber) {
        return paymentOrderService.cancelTopUp(accountId(userId), invoiceNumber);
    }

    @PostMapping("/ipn")
    public ResponseEntity<Map<String, Boolean>> receiveIpn(
            @RequestHeader(value = "X-Secret-Key", required = false) String secretKey,
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayIpnRequest request) {
        paymentOrderService.handleIpn(firstSecret(secretKey, apiKey, authorization), request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private String firstSecret(String secretKey, String apiKey, String authorization) {
        if (StringUtils.hasText(secretKey)) return secretKey;
        if (StringUtils.hasText(apiKey)) return apiKey;
        if (!StringUtils.hasText(authorization)) return null;
        if (authorization.regionMatches(true, 0, "Apikey ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private UUID accountId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authenticated account is required");
        }
    }
}
