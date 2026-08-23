package iuh.fit.se.walletservice.controller;

import iuh.fit.se.walletservice.dto.response.PaymentOrderPageResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import iuh.fit.se.walletservice.service.PaymentOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Admin audit view for SePay checkout and IPN-correlated payment orders. */
@RestController
@RequestMapping("/api/v1/admin/sepay/top-ups")
public class AdminPaymentOrderController {
    private final PaymentOrderService paymentOrderService;

    public AdminPaymentOrderController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @GetMapping
    public PaymentOrderPageResponse list(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireAdmin(adminId, role);
        return paymentOrderService.listAdmin(status, page, pageSize);
    }

    @GetMapping("/{invoiceNumber}")
    public PaymentOrderResponse get(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String invoiceNumber) {
        requireAdmin(adminId, role);
        return paymentOrderService.getAdmin(invoiceNumber);
    }

    private UUID requireAdmin(String adminId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required");
        }
        try {
            return UUID.fromString(adminId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator is required");
        }
    }
}
