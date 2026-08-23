package iuh.fit.se.walletservice.service;

import iuh.fit.se.walletservice.domain.entity.PaymentOrder;
import iuh.fit.se.walletservice.domain.entity.Transaction;
import iuh.fit.se.walletservice.domain.entity.Wallet;
import iuh.fit.se.walletservice.domain.enums.PaymentMethod;
import iuh.fit.se.walletservice.domain.enums.PaymentOrderStatus;
import iuh.fit.se.walletservice.dto.request.SepayIpnRequest;
import iuh.fit.se.walletservice.dto.request.TopUpRequest;
import iuh.fit.se.walletservice.dto.response.TopUpResponse;
import iuh.fit.se.walletservice.dto.response.TopUpStatusResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderPageResponse;
import iuh.fit.se.walletservice.dto.response.PaymentOrderResponse;
import iuh.fit.se.walletservice.mapper.TopUpMapper;
import iuh.fit.se.walletservice.repository.PaymentOrderRepository;
import iuh.fit.se.walletservice.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Owns provider correlation, IPN verification and the single balance credit point. */
@Service
public class PaymentOrderService {
    private static final String PROVIDER = "SEPAY";

    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletRepository walletRepository;
    private final SepayCheckoutService sepayCheckoutService;
    private final TopUpMapper topUpMapper;
    private final String ipnSecret;

    public PaymentOrderService(
            PaymentOrderRepository paymentOrderRepository,
            WalletRepository walletRepository,
            SepayCheckoutService sepayCheckoutService,
            TopUpMapper topUpMapper,
            @Value("${sepay.ipn-secret:}") String ipnSecret) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.walletRepository = walletRepository;
        this.sepayCheckoutService = sepayCheckoutService;
        this.topUpMapper = topUpMapper;
        this.ipnSecret = ipnSecret;
    }

    @Transactional
    public TopUpResponse createTopUp(UUID accountId, TopUpRequest request) {
        validateAmount(request.amount());
        Instant now = Instant.now();
        PaymentOrder order = paymentOrderRepository.saveAndFlush(PaymentOrder.builder()
                .accountId(accountId)
                .invoiceNumber(invoiceNumber(now))
                .amount(request.amount().setScale(2))
                .provider(PROVIDER)
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build());

        var formFields = sepayCheckoutService.createForm(order, request.returnUrl());
        order.setCheckoutUrl(sepayCheckoutService.checkoutUrl());
        paymentOrderRepository.save(order);
        return topUpMapper.toResponse(order, formFields);
    }

    @Transactional(readOnly = true)
    public TopUpStatusResponse getTopUpStatus(UUID accountId, String invoiceNumber) {
        PaymentOrder order = paymentOrderRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
        if (!order.getAccountId().equals(accountId)) {
            // Same status as a missing order so callers cannot probe other accounts' invoices.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found");
        }
        return new TopUpStatusResponse(
                order.getInvoiceNumber(),
                order.getStatus().name(),
                order.getAmount(),
                order.getPaidAt(),
                order.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public PaymentOrderPageResponse listMine(UUID accountId, int page, int pageSize) {
        Page<PaymentOrder> orders = paymentOrderRepository.findByAccountId(accountId, pageable(page, pageSize));
        return page(orders, page, pageSize);
    }

    @Transactional(readOnly = true)
    public PaymentOrderPageResponse listAdmin(String status, int page, int pageSize) {
        Page<PaymentOrder> orders = StringUtils.hasText(status)
                ? paymentOrderRepository.findByStatus(parseStatus(status), pageable(page, pageSize))
                : paymentOrderRepository.findAll(pageable(page, pageSize));
        return page(orders, page, pageSize);
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse getAdmin(String invoiceNumber) {
        return toResponse(paymentOrderRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found")));
    }

    @Transactional
    public PaymentOrderResponse cancelTopUp(UUID accountId, String invoiceNumber) {
        PaymentOrder order = paymentOrderRepository.findByInvoiceNumberForUpdate(invoiceNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
        if (!order.getAccountId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found");
        }
        if (order.getStatus() != PaymentOrderStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only created payment orders can be cancelled");
        }
        if (order.getExpiresAt().isBefore(Instant.now())) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            paymentOrderRepository.save(order);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment order has expired");
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        return toResponse(paymentOrderRepository.save(order));
    }

    @Transactional
    public void handleIpn(String suppliedSecret, SepayIpnRequest request) {
        authorizeIpn(suppliedSecret);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SePay IPN payload");
        }
        if (request.isVoidNotification()) {
            return;
        }
        if (!request.isPaidNotification()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported SePay notification");
        }

        String invoiceNumber = request.invoiceNumber();
        String paymentAmount = request.paymentAmount();
        String providerTransactionId = request.providerTransactionId();
        if (invoiceNumber == null || paymentAmount == null || providerTransactionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incomplete SePay payment payload");
        }
        PaymentOrder order = paymentOrderRepository
                .findByInvoiceNumberForUpdate(invoiceNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return;
        }
        if (order.getExpiresAt().isBefore(Instant.now())) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment order has expired");
        }
        BigDecimal providerAmount = parseAmount(paymentAmount);
        if (order.getAmount().compareTo(providerAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Payment amount does not match order");
        }

        Wallet wallet = walletRepository.findByAccountId(order.getAccountId())
                .orElseGet(() -> Wallet.builder().accountId(order.getAccountId()).build());
        Transaction transaction = wallet.deposit(providerAmount);
        transaction.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        transaction.setReferenceCode("SEPAY:" + providerTransactionId);
        transaction.setDescription("SePay top-up " + order.getInvoiceNumber());

        order.setStatus(PaymentOrderStatus.PAID);
        order.setProviderTransactionId(providerTransactionId);
        order.setPaidAt(Instant.now());
        walletRepository.saveAndFlush(wallet);
        paymentOrderRepository.save(order);
    }

    private void authorizeIpn(String suppliedSecret) {
        if (!StringUtils.hasText(ipnSecret)
                || !StringUtils.hasText(suppliedSecret)
                || !MessageDigest.isEqual(
                ipnSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SePay secret key");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Top-up amount must be a positive whole VND amount");
        }
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount).setScale(2);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SePay amount");
        }
    }

    private static String invoiceNumber(Instant now) {
        return "BBTOPUP_" + now.getEpochSecond() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private PaymentOrderPageResponse page(Page<PaymentOrder> result, int page, int pageSize) {
        return new PaymentOrderPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                page,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages());
    }

    private PaymentOrderResponse toResponse(PaymentOrder order) {
        return new PaymentOrderResponse(
                order.getId(),
                order.getAccountId(),
                order.getInvoiceNumber(),
                order.getAmount(),
                order.getProvider(),
                order.getStatus().name(),
                order.getCheckoutUrl(),
                order.getProviderTransactionId(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private Pageable pageable(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PaymentOrderStatus parseStatus(String status) {
        try {
            return PaymentOrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment order status");
        }
    }
}
