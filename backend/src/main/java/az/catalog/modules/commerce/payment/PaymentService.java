package az.catalog.modules.commerce.payment;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.commerce.order.Order;
import az.catalog.modules.commerce.order.OrderRepository;
import az.catalog.modules.commerce.order.OrderService;
import az.catalog.modules.commerce.order.OrderStatus;
import az.catalog.modules.commerce.order.OrderStatusHistory;
import az.catalog.modules.commerce.order.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives payments through whichever {@link PaymentProvider} is configured.
 *
 * <p>Three properties this class is responsible for:
 * <ul>
 *   <li><b>Idempotency</b> — every callback is recorded as a {@link PaymentEvent} first;
 *       a redelivered event is recognised by its provider event id and skipped.</li>
 *   <li><b>Authenticity</b> — a callback whose signature does not verify is stored for
 *       audit but never moves money or order state.</li>
 *   <li><b>Amount matching</b> — a callback reporting a different amount than the order
 *       is rejected, which is the classic tampering vector on redirect-based flows.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final List<PaymentProvider> providers;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    // =======================================================================
    // Initiation
    // =======================================================================

    /**
     * Opens a payment session for an order. Re-initiating an order that already has an
     * open session returns that session rather than creating a second one, so a shopper
     * refreshing the payment page does not spawn duplicates at the provider.
     */
    @Transactional
    public PaymentSession initiate(Order order, String language) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
        }
        if (order.paymentWindowExpired()) {
            throw new ApiException(ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        List<Payment> open = paymentRepository.findOpenForOrder(order.getId());
        if (!open.isEmpty() && open.get(0).getPaymentUrl() != null) {
            Payment existing = open.get(0);
            log.debug("Reusing open payment {} for order {}", existing.getReference(), order.getOrderNumber());
            return new PaymentSession(existing.getReference(), existing.getPaymentUrl(), existing.getId());
        }

        PaymentProvider provider = provider();
        String reference = generateReference(order);

        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .provider(provider.name())
                .reference(reference)
                .amount(order.getTotal())
                .currency(order.getCurrency())
                .status(Payment.Status.CREATED)
                .build());

        var request = new PaymentProvider.InitiationRequest(
                reference,
                order.getTotal(),
                order.getCurrency(),
                order.getOrderNumber(),
                "Sifariş " + order.getOrderNumber(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                properties.getPayment().getReturnUrl() + "?reference=" + reference,
                properties.getApiUrl() + "/api/public/payments/callback/" + provider.name(),
                language);

        payment.setRequestPayload(objectMapper.valueToTree(Map.of(
                "reference", reference,
                "amount", order.getTotal(),
                "currency", order.getCurrency(),
                "orderNumber", order.getOrderNumber())));

        PaymentProvider.InitiationResult result;
        try {
            result = provider.initiate(request);
        } catch (RuntimeException ex) {
            log.error("Provider {} failed to initiate payment for order {}",
                    provider.name(), order.getOrderNumber(), ex);
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailureMessage(ex.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }

        if (!result.success()) {
            payment.setStatus(Payment.Status.FAILED);
            payment.setFailureCode(result.failureCode());
            payment.setFailureMessage(result.failureMessage());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR,
                    result.failureMessage() == null
                            ? ErrorCode.PAYMENT_PROVIDER_ERROR.getDefaultMessage()
                            : result.failureMessage());
        }

        payment.setStatus(Payment.Status.PENDING);
        payment.setPaymentUrl(result.paymentUrl());
        payment.setExternalTransactionId(result.externalTransactionId());
        if (result.rawResponse() != null) {
            payment.setResponsePayload(objectMapper.valueToTree(result.rawResponse()));
        }

        log.info("Initiated payment {} for order {} via {}",
                reference, order.getOrderNumber(), provider.name());

        return new PaymentSession(reference, result.paymentUrl(), payment.getId());
    }

    /** Re-opens payment for an order the shopper abandoned but whose window is still open. */
    @Transactional
    public PaymentSession retry(Long orderId, String language) {
        Order order = orderService.require(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT && order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE, "Bu sifariş üçün ödəniş açıla bilməz");
        }
        return initiate(order, language);
    }

    // =======================================================================
    // Callbacks
    // =======================================================================

    /**
     * Processes a provider callback or webhook.
     *
     * Always returns normally: a provider must receive 200 even for an event we choose to
     * ignore, otherwise it retries forever. Problems are recorded on the event row.
     */
    @Transactional
    public CallbackOutcome handleCallback(String providerName, Map<String, String> payload,
                                          String signature) {
        PaymentProvider provider = provider(providerName);
        PaymentProvider.CallbackResult result = provider.handleCallback(payload, signature);

        // Duplicate delivery: recognised and dropped before it can move any state.
        if (result.externalEventId() != null
                && eventRepository.existsByProviderAndExternalEventId(providerName, result.externalEventId())) {
            log.debug("Ignoring duplicate {} event {}", providerName, result.externalEventId());
            return new CallbackOutcome(true, result.reference(), "DUPLICATE", null);
        }

        PaymentEvent event = eventRepository.save(PaymentEvent.builder()
                .provider(providerName)
                .eventType(result.outcome().name())
                .externalEventId(result.externalEventId())
                .payload(objectMapper.valueToTree(payload))
                .signatureValid(result.signatureValid())
                .build());

        if (!result.signatureValid()) {
            // Stored for audit, but nothing downstream happens.
            event.setErrorMessage("Signature verification failed");
            log.warn("Rejected {} callback for reference {}: bad signature",
                    providerName, result.reference());
            return new CallbackOutcome(false, result.reference(), "INVALID_SIGNATURE",
                    "İmza yoxlanışı uğursuz oldu");
        }

        Payment payment = paymentRepository.findByReference(result.reference()).orElse(null);
        if (payment == null) {
            event.setErrorMessage("Unknown payment reference: " + result.reference());
            log.warn("Callback for unknown payment reference {}", result.reference());
            return new CallbackOutcome(false, result.reference(), "UNKNOWN_PAYMENT", null);
        }
        event.setPaymentId(payment.getId());

        Order order = orderRepository.findDetailById(payment.getOrderId()).orElse(null);
        if (order == null) {
            event.setErrorMessage("Payment has no order");
            return new CallbackOutcome(false, result.reference(), "UNKNOWN_ORDER", null);
        }

        // Amount tampering check: a redirect flow lets the shopper see (and try to edit)
        // the parameters, so what the provider reports must match what we charged.
        if (result.amount() != null && result.amount().compareTo(payment.getAmount()) != 0) {
            event.setErrorMessage("Amount mismatch: expected %s, received %s"
                    .formatted(payment.getAmount(), result.amount()));
            log.error("Amount mismatch on payment {}: expected {}, provider reported {}",
                    payment.getReference(), payment.getAmount(), result.amount());
            return new CallbackOutcome(false, result.reference(), "AMOUNT_MISMATCH", null);
        }

        // Already settled: a later duplicate must not re-run the side effects.
        if (payment.getStatus() == Payment.Status.SUCCEEDED
                && result.outcome() == PaymentProvider.PaymentOutcome.SUCCEEDED) {
            event.setProcessed(true);
            event.setProcessedAt(OffsetDateTime.now());
            return new CallbackOutcome(true, result.reference(), "ALREADY_SETTLED", null);
        }

        applyOutcome(payment, order, result);

        event.setProcessed(true);
        event.setProcessedAt(OffsetDateTime.now());
        payment.setCallbackPayload(objectMapper.valueToTree(payload));

        return new CallbackOutcome(true, result.reference(), result.outcome().name(), null);
    }

    private void applyOutcome(Payment payment, Order order, PaymentProvider.CallbackResult result) {
        switch (result.outcome()) {
            case SUCCEEDED -> {
                payment.setStatus(Payment.Status.SUCCEEDED);
                payment.setCompletedAt(OffsetDateTime.now());
                payment.setCardMask(result.cardMask());
                payment.setCardBrand(result.cardBrand());
                if (result.externalTransactionId() != null) {
                    payment.setExternalTransactionId(result.externalTransactionId());
                }
                orderService.markPaid(order, OrderStatusHistory.ActorType.PAYMENT_PROVIDER);
            }
            case AUTHORIZED -> {
                payment.setStatus(Payment.Status.AUTHORIZED);
                order.setPaymentStatus(PaymentStatus.AUTHORIZED);
            }
            case FAILED -> {
                payment.setStatus(Payment.Status.FAILED);
                payment.setFailureCode(result.failureCode());
                payment.setFailureMessage(result.failureMessage());
                orderService.markPaymentFailed(order,
                        result.failureMessage() == null ? "Naməlum səbəb" : result.failureMessage());
            }
            case CANCELLED -> {
                payment.setStatus(Payment.Status.CANCELLED);
                orderService.markPaymentFailed(order, "Ödəniş ləğv edildi");
            }
            case REFUNDED -> {
                payment.setStatus(Payment.Status.REFUNDED);
                payment.setRefundedAmount(payment.getAmount());
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            case PENDING, UNKNOWN -> payment.setStatus(Payment.Status.PENDING);
        }
    }

    // =======================================================================
    // Status / verification
    // =======================================================================

    /**
     * What the shopper's browser asks when it lands back on the result page — often before
     * the webhook has arrived, so this also asks the provider directly.
     */
    @Transactional
    public PaymentStatusView status(String reference) {
        Payment payment = paymentRepository.findByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = orderRepository.findDetailById(payment.getOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.isFinal()) {
            try {
                var verification = provider(payment.getProvider())
                        .verify(reference, payment.getExternalTransactionId());
                if (verification.outcome() == PaymentProvider.PaymentOutcome.SUCCEEDED) {
                    payment.setStatus(Payment.Status.SUCCEEDED);
                    payment.setCompletedAt(OffsetDateTime.now());
                    orderService.markPaid(order, OrderStatusHistory.ActorType.PAYMENT_PROVIDER);
                }
            } catch (RuntimeException ex) {
                // Verification is best-effort; the webhook remains the authority.
                log.debug("Verification failed for {}: {}", reference, ex.getMessage());
            }
        }

        return new PaymentStatusView(
                payment.getReference(), payment.getStatus().name(), order.getId(),
                order.getOrderNumber(), order.getStatus(), order.getStatus().label(),
                order.getPaymentStatus(), order.getTotal(), order.getCurrency(),
                payment.getFailureMessage());
    }

    // =======================================================================
    // Refunds
    // =======================================================================

    @Transactional
    public void refund(Long orderId, BigDecimal amount, String reason) {
        Order order = orderService.require(orderId);
        Payment payment = paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(candidate -> candidate.getStatus() == Payment.Status.SUCCEEDED
                        || candidate.getStatus() == Payment.Status.PARTIALLY_REFUNDED)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Bu sifariş üçün geri qaytarıla bilən ödəniş yoxdur"));

        BigDecimal refundAmount = amount == null ? payment.refundableAmount() : amount;
        if (refundAmount.signum() <= 0 || refundAmount.compareTo(payment.refundableAmount()) > 0) {
            throw ApiException.badRequest("Geri qaytarılacaq məbləğ yanlışdır");
        }

        var result = provider(payment.getProvider())
                .refund(payment.getReference(), payment.getExternalTransactionId(), refundAmount, reason);

        if (!result.success()) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR, result.failureMessage());
        }

        payment.setRefundedAmount(payment.getRefundedAmount().add(refundAmount));
        boolean full = payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0;
        payment.setStatus(full ? Payment.Status.REFUNDED : Payment.Status.PARTIALLY_REFUNDED);

        order.setPaymentStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        if (full && order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            orderService.applyTransition(order, OrderStatus.REFUNDED, reason,
                    OrderStatusHistory.ActorType.ADMIN);
        }
        log.info("Refunded {} {} on order {}", refundAmount, order.getCurrency(), order.getOrderNumber());
    }

    @Transactional(readOnly = true)
    public List<Payment> forOrder(Long orderId) {
        return paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId);
    }

    // =======================================================================
    // Internals
    // =======================================================================

    private PaymentProvider provider() {
        return provider(properties.getPayment().getProvider());
    }

    private PaymentProvider provider(String name) {
        return providers.stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR,
                        "Ödəniş provayderi tapılmadı: " + name));
    }

    /**
     * Reference is unique per attempt, not per order: a retried payment gets a fresh one
     * so the provider never sees the same reference with two different outcomes.
     */
    private String generateReference(Order order) {
        return "%s-%s".formatted(order.getOrderNumber(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    // --- view models --------------------------------------------------------

    public record PaymentSession(String reference, String paymentUrl, Long paymentId) {
    }

    public record CallbackOutcome(boolean accepted, String reference, String result, String message) {
    }

    public record PaymentStatusView(
            String reference,
            String paymentStatus,
            Long orderId,
            String orderNumber,
            OrderStatus orderStatus,
            String orderStatusLabel,
            PaymentStatus orderPaymentStatus,
            BigDecimal total,
            String currency,
            String failureMessage
    ) {
    }
}
