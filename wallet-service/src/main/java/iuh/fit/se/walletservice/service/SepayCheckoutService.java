package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the signed SePay checkout form. SePay receives this form directly in
 * the browser; the wallet balance is changed only by the verified IPN handler.
 */
@Service
public class SepayCheckoutService {
    private final boolean enabled;
    private final String merchantId;
    private final String secretKey;
    private final String checkoutUrl;
    private final String successUrl;
    private final String errorUrl;
    private final String cancelUrl;

    public SepayCheckoutService(
            @Value("${sepay.enabled:false}") boolean enabled,
            @Value("${sepay.merchant-id:}") String merchantId,
            @Value("${sepay.secret-key:}") String secretKey,
            @Value("${sepay.checkout-url:https://pay-sandbox.sepay.vn/v1/checkout/init}") String checkoutUrl,
            @Value("${sepay.success-url:http://localhost:3000/carrier/wallet?payment=success}") String successUrl,
            @Value("${sepay.error-url:http://localhost:3000/carrier/wallet?payment=error}") String errorUrl,
            @Value("${sepay.cancel-url:http://localhost:3000/carrier/wallet?payment=cancel}") String cancelUrl) {
        this.enabled = enabled;
        this.merchantId = merchantId;
        this.secretKey = secretKey;
        this.checkoutUrl = checkoutUrl;
        this.successUrl = successUrl;
        this.errorUrl = errorUrl;
        this.cancelUrl = cancelUrl;
    }

    public Map<String, String> createForm(PaymentOrder order, String returnUrl) {
        ensureConfigured();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("order_amount", formatAmount(order.getAmount()));
        fields.put("merchant", merchantId);
        fields.put("currency", "VND");
        fields.put("operation", "PURCHASE");
        fields.put("order_description", "Nap tien vi BackHaulBid " + order.getInvoiceNumber());
        fields.put("order_invoice_number", order.getInvoiceNumber());
        fields.put("customer_id", order.getAccountId().toString());
        
        if (returnUrl != null && !returnUrl.isBlank()) {
            fields.put("success_url", withPaymentStatus(returnUrl, "success"));
            fields.put("error_url", withPaymentStatus(returnUrl, "error"));
            fields.put("cancel_url", withPaymentStatus(returnUrl, "cancel"));
        } else {
            fields.put("success_url", successUrl);
            fields.put("error_url", errorUrl);
            fields.put("cancel_url", cancelUrl);
        }
        
        fields.put("signature", sign(fields));
        return fields;
    }

    public String checkoutUrl() {
        ensureConfigured();
        return checkoutUrl;
    }

    private void ensureConfigured() {
        if (!enabled || merchantId.isBlank() || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SePay Sandbox is not configured");
        }
    }

    private String sign(Map<String, String> fields) {
        String[] signedFieldNames = {
                "order_amount", "merchant", "currency", "operation",
                "order_description", "order_invoice_number", "customer_id",
                "payment_method", "success_url", "error_url", "cancel_url"
        };
        String signed = java.util.Arrays.stream(signedFieldNames)
                .filter(fields::containsKey)
                .map(field -> field + "=" + fields.get(field))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign SePay checkout form", exception);
        }
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Top-up amount must be a whole VND amount");
        }
        return amount.setScale(0).toPlainString();
    }

    private static String withPaymentStatus(String returnUrl, String status) {
        return returnUrl + (returnUrl.contains("?") ? "&" : "?") + "payment=" + status;
    }
}
