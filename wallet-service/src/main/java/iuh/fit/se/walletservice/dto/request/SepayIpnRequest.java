package iuh.fit.se.walletservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accepts both the legacy checkout callback and SePay's standard flat webhook. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SepayIpnRequest(
        String timestamp,
        @JsonProperty("notification_type") String notificationType,
        SepayOrder order,
        SepayTransaction transaction,
        @JsonProperty("id") String providerId,
        @JsonAlias({"code", "transferCode"}) String code,
        String content,
        String description,
        @JsonProperty("referenceCode") String referenceCode,
        @JsonAlias({"transferAmount", "transfer_amount"}) String transferAmount,
        @JsonAlias({"transferType", "transfer_type"}) String transferType,
        @JsonProperty("transactionDate") String transactionDate,
        String status
) {
    private static final Pattern INVOICE_PATTERN = Pattern.compile("BBTOPUP_[A-Za-z0-9]+_[A-Za-z0-9]+");

    public record SepayOrder(
            @JsonProperty("order_invoice_number") String invoiceNumber,
            @JsonProperty("order_status") String status,
            @JsonProperty("order_amount") String amount
    ) {
    }

    public record SepayTransaction(
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("transaction_status") String status,
            @JsonProperty("transaction_amount") String amount
    ) {
    }

    public boolean isVoidNotification() {
        return "TRANSACTION_VOID".equalsIgnoreCase(notificationType);
    }

    public boolean isPaidNotification() {
        if (notificationType != null && !notificationType.isBlank()) {
            return "ORDER_PAID".equalsIgnoreCase(notificationType);
        }
        return "IN".equalsIgnoreCase(transferType) && positive(transferAmount);
    }

    public String invoiceNumber() {
        if (order != null && hasText(order.invoiceNumber())) {
            return order.invoiceNumber();
        }
        return firstInvoice(code, content, description, referenceCode);
    }

    public String paymentAmount() {
        if (order != null && hasText(order.amount())) {
            return order.amount();
        }
        return transferAmount;
    }

    public String providerTransactionId() {
        if (transaction != null && hasText(transaction.transactionId())) {
            return transaction.transactionId();
        }
        return providerId;
    }

    private static String firstInvoice(String... values) {
        for (String value : values) {
            if (!hasText(value)) continue;
            Matcher matcher = INVOICE_PATTERN.matcher(value);
            if (matcher.find()) return matcher.group();
        }
        return null;
    }

    private static boolean positive(String value) {
        try {
            return value != null && new BigDecimal(value).signum() > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
