package iuh.fit.se.walletservice.mapper;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import iuh.fit.se.walletservice.domain.entity.WithdrawalRequest;
import iuh.fit.se.walletservice.domain.enums.PaymentOrderStatus;
import iuh.fit.se.walletservice.domain.enums.WithdrawalStatus;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import iuh.fit.se.walletservice.dto.response.TopUpResponse;
import iuh.fit.se.walletservice.dto.response.TopUpStatusResponse;
import iuh.fit.se.walletservice.dto.response.WithdrawalResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletMappersFunctionalTest {

    private final TopUpMapper topUpMapper = Mappers.getMapper(TopUpMapper.class);
    private final WithdrawalMapper withdrawalMapper = Mappers.getMapper(WithdrawalMapper.class);

    @Test
    void topUpMapperMapsCheckoutStatusAndPaymentOrderResponses() {
        UUID orderId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        PaymentOrder order = PaymentOrder.builder()
                .id(orderId)
                .accountId(accountId)
                .invoiceNumber("BBTOPUP_123456")
                .amount(new BigDecimal("2000000.00"))
                .provider("SEPAY")
                .status(PaymentOrderStatus.PAID)
                .checkoutUrl("https://pay.sepay.vn/checkout")
                .providerTransactionId("SEPAY_TX_01")
                .expiresAt(now.plusSeconds(900))
                .paidAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        TopUpResponse checkout = topUpMapper.toResponse(order, Map.of("merchant", "backhaulbid"));
        assertThat(checkout.paymentOrderId()).isEqualTo(orderId);
        assertThat(checkout.status()).isEqualTo("PAID");
        assertThat(checkout.formFields()).containsEntry("merchant", "backhaulbid");

        TopUpStatusResponse status = topUpMapper.toTopUpStatusResponse(order);
        assertThat(status.invoiceNumber()).isEqualTo("BBTOPUP_123456");
        assertThat(status.status()).isEqualTo("PAID");
        assertThat(status.amount()).isEqualByComparingTo("2000000.00");

        PaymentOrderResponse detail = topUpMapper.toPaymentOrderResponse(order);
        assertThat(detail.paymentOrderId()).isEqualTo(orderId);
        assertThat(detail.accountId()).isEqualTo(accountId);
        assertThat(detail.providerTransactionId()).isEqualTo("SEPAY_TX_01");
    }

    @Test
    void withdrawalMapperMasksBankAccountNumbersCorrectly() {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("1500000.00"))
                .bankName("Vietcombank")
                .bankAccountNumber("0071001234567")
                .accountHolderName("NGUYEN VAN A")
                .status(WithdrawalStatus.PENDING)
                .build();

        WithdrawalResponse response = withdrawalMapper.toResponse(request);
        assertThat(response.maskedBankAccountNumber()).isEqualTo("*********4567");
        assertThat(response.status()).isEqualTo("PENDING");

        WithdrawalRequest shortNumber = WithdrawalRequest.builder()
                .bankAccountNumber("1234")
                .status(WithdrawalStatus.REJECTED)
                .build();
        assertThat(withdrawalMapper.toResponse(shortNumber).maskedBankAccountNumber()).isEqualTo("****");
    }
}
