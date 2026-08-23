package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import iuh.fit.se.walletservice.domain.enums.PaymentOrderStatus;
import iuh.fit.se.walletservice.dto.response.PaymentOrderPageResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import iuh.fit.se.walletservice.repository.PaymentOrderRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import iuh.fit.se.walletservice.mapper.TopUpMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderMissingApiTest {
    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private SepayCheckoutService sepayCheckoutService;

    @Mock
    private TopUpMapper topUpMapper;

    private PaymentOrderService service;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        service = new PaymentOrderService(
                paymentOrderRepository,
                walletRepository,
                sepayCheckoutService,
                topUpMapper,
                "test-secret");
        accountId = UUID.randomUUID();
    }

    @Test
    void givenMyPaymentOrders_whenListing_thenReturnsPaginatedHistory() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .invoiceNumber("BBTOPUP_history_001")
                .amount(new BigDecimal("50000.00"))
                .provider("SEPAY")
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(paymentOrderRepository.findByAccountId(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PaymentOrderPageResponse result = service.listMine(accountId, 1, 20);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().invoiceNumber()).isEqualTo("BBTOPUP_history_001");
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void givenCreatedOrderOwnedByAccount_whenCancelling_thenMarksOrderCancelled() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .invoiceNumber("BBTOPUP_cancel_001")
                .amount(new BigDecimal("50000.00"))
                .provider("SEPAY")
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(paymentOrderRepository.findByInvoiceNumberForUpdate(order.getInvoiceNumber()))
                .thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(order)).thenReturn(order);

        PaymentOrderResponse result = service.cancelTopUp(accountId, order.getInvoiceNumber());

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
    }

    @Test
    void givenPaidOrder_whenCancelling_thenRejectsWithoutChangingStatus() {
        PaymentOrder order = PaymentOrder.builder()
                .accountId(accountId)
                .invoiceNumber("BBTOPUP_cancel_paid")
                .amount(new BigDecimal("50000.00"))
                .provider("SEPAY")
                .status(PaymentOrderStatus.PAID)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(paymentOrderRepository.findByInvoiceNumberForUpdate(order.getInvoiceNumber()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelTopUp(accountId, order.getInvoiceNumber()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(409);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
    }
}
