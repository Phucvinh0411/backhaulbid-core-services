package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.enums.PaymentOrderStatus;
import iuh.fit.se.walletservice.dto.request.SepayIpnRequest;
import iuh.fit.se.walletservice.dto.request.TopUpRequest;
import iuh.fit.se.walletservice.dto.response.TopUpResponse;
import iuh.fit.se.walletservice.mapper.TopUpMapper;
import iuh.fit.se.walletservice.repository.PaymentOrderRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private SepayCheckoutService sepayCheckoutService;

    @Mock
    private TopUpMapper topUpMapper;

    private PaymentOrderService paymentOrderService;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        paymentOrderService = new PaymentOrderService(
                paymentOrderRepository,
                walletRepository,
                sepayCheckoutService,
                topUpMapper,
                "test-secret"
        );
        accountId = UUID.randomUUID();
    }

    /**
     * Feature: Carrier manages wallet
     * Scenario: Create top-up order
     */
    @Test
    void givenValidRequest_whenCreateTopUp_thenSaveAndReturnResponse() {
        TopUpRequest request = new TopUpRequest(new BigDecimal("50000"), "http://localhost:3000/carrier/wallet");
        PaymentOrder order = PaymentOrder.builder()
                .accountId(accountId)
                .amount(request.amount())
                .status(PaymentOrderStatus.CREATED)
                .build();
        Map<String, String> formFields = new HashMap<>();
        formFields.put("success_url", "http://localhost:3000/carrier/wallet?payment=success");

        when(paymentOrderRepository.saveAndFlush(any(PaymentOrder.class))).thenReturn(order);
        when(sepayCheckoutService.createForm(order, "http://localhost:3000/carrier/wallet")).thenReturn(formFields);
        when(sepayCheckoutService.checkoutUrl()).thenReturn("http://sepay.url/checkout");
        when(topUpMapper.toResponse(any(PaymentOrder.class), eq(formFields)))
                .thenReturn(new TopUpResponse(
                        UUID.randomUUID(), 
                        "INV123", 
                        new BigDecimal("50000"), 
                        "CREATED", 
                        "http://sepay.url/checkout", 
                        formFields, 
                        java.time.Instant.now()
                ));

        TopUpResponse response = paymentOrderService.createTopUp(accountId, request);

        assertNotNull(response);
        assertEquals("http://sepay.url/checkout", response.checkoutUrl());
        verify(paymentOrderRepository).save(order);
        verify(sepayCheckoutService).createForm(order, request.returnUrl());
    }

    @Test
    void givenStandardFlatWebhook_whenPaymentIsIncoming_thenCreditWallet() {
        String invoiceNumber = "BBTOPUP_1787302000_ab12cd34";
        PaymentOrder order = PaymentOrder.builder()
                .accountId(accountId)
                .invoiceNumber(invoiceNumber)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(java.time.Instant.now().plusSeconds(900))
                .build();
        Wallet wallet = Wallet.builder()
                .accountId(accountId)
                .balance(new BigDecimal("100000.00"))
                .build();
        SepayIpnRequest request = new SepayIpnRequest(
                "1787302000",
                null,
                null,
                null,
                "sepay-tx-001",
                invoiceNumber,
                "Thanh toan " + invoiceNumber,
                null,
                null,
                "50000",
                "in",
                "2026-08-21T10:00:00+07:00",
                null
        );

        when(paymentOrderRepository.findByInvoiceNumberForUpdate(invoiceNumber)).thenReturn(Optional.of(order));
        when(walletRepository.findByAccountId(accountId)).thenReturn(Optional.of(wallet));

        paymentOrderService.handleIpn("test-secret", request);

        assertEquals(PaymentOrderStatus.PAID, order.getStatus());
        assertEquals(new BigDecimal("150000.00"), wallet.getBalance());
        assertEquals("sepay-tx-001", order.getProviderTransactionId());
        verify(walletRepository).saveAndFlush(wallet);
        verify(paymentOrderRepository).save(order);
    }

    @Test
    void givenInvalidIpnSecret_whenPaymentIsIncoming_thenRejectWithoutChangingWallet() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentOrderService.handleIpn("wrong-secret", null)
        );

        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(paymentOrderRepository, walletRepository);
    }

    @Test
    void givenAlreadyPaidOrder_whenDuplicateIpnArrives_thenDoNotCreditWalletAgain() {
        String invoiceNumber = "BBTOPUP_duplicate_001";
        PaymentOrder order = PaymentOrder.builder()
                .accountId(accountId)
                .invoiceNumber(invoiceNumber)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentOrderStatus.PAID)
                .expiresAt(java.time.Instant.now().plusSeconds(900))
                .build();
        SepayIpnRequest request = new SepayIpnRequest(
                "1787302001", null, null, null, "sepay-tx-duplicate", invoiceNumber,
                "Thanh toan " + invoiceNumber, null, null, "50000", "in",
                "2026-08-21T10:00:00+07:00", null
        );
        when(paymentOrderRepository.findByInvoiceNumberForUpdate(invoiceNumber)).thenReturn(Optional.of(order));

        paymentOrderService.handleIpn("test-secret", request);

        verifyNoInteractions(walletRepository);
        verify(paymentOrderRepository).findByInvoiceNumberForUpdate(invoiceNumber);
        verify(paymentOrderRepository, never()).save(order);
    }

    @Test
    void givenAmountDoesNotMatchOrder_whenIpnArrives_thenRejectWithoutCredit() {
        String invoiceNumber = "BBTOPUP_amount_001";
        PaymentOrder order = PaymentOrder.builder()
                .accountId(accountId)
                .invoiceNumber(invoiceNumber)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(java.time.Instant.now().plusSeconds(900))
                .build();
        SepayIpnRequest request = new SepayIpnRequest(
                "1787302002", null, null, null, "sepay-tx-amount", invoiceNumber,
                "Thanh toan " + invoiceNumber, null, null, "50001", "in",
                "2026-08-21T10:00:00+07:00", null
        );
        when(paymentOrderRepository.findByInvoiceNumberForUpdate(invoiceNumber)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentOrderService.handleIpn("test-secret", request)
        );

        assertEquals(422, exception.getStatusCode().value());
        verifyNoInteractions(walletRepository);
        verify(paymentOrderRepository, never()).save(order);
    }

    @Test
    void givenExpiredOrder_whenPaidIpnArrives_thenRejectWithConflict() {
        String invoiceNumber = "BBTOPUP_expired_001";
        PaymentOrder order = PaymentOrder.builder()
                .accountId(accountId)
                .invoiceNumber(invoiceNumber)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(java.time.Instant.now().minusSeconds(1))
                .build();
        SepayIpnRequest request = new SepayIpnRequest(
                "1787302003", null, null, null, "sepay-tx-expired", invoiceNumber,
                null, null, null, "50000", "in", null, null
        );
        when(paymentOrderRepository.findByInvoiceNumberForUpdate(invoiceNumber)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentOrderService.handleIpn("test-secret", request)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(PaymentOrderStatus.EXPIRED, order.getStatus());
        verifyNoInteractions(walletRepository);
    }

    @Test
    void givenOutgoingTransfer_whenIpnArrives_thenRejectWithoutLookingUpOrder() {
        SepayIpnRequest request = new SepayIpnRequest(
                "1787302004", null, null, null, "sepay-tx-out", "BBTOPUP_outgoing_001",
                null, null, null, "50000", "out", null, null
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentOrderService.handleIpn("test-secret", request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(paymentOrderRepository, walletRepository);
    }

    @Test
    void givenIncompletePaidPayload_whenIpnArrives_thenRejectAsBadRequest() {
        SepayIpnRequest request = new SepayIpnRequest(
                "1787302005", null, null, null, null, null,
                null, null, null, "50000", "in", null, null
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentOrderService.handleIpn("test-secret", request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(paymentOrderRepository, walletRepository);
    }
}
