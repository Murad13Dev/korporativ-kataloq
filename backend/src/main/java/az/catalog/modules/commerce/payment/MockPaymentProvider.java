package az.catalog.modules.commerce.payment;

import az.catalog.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Fully working in-house provider used for development, demos and automated tests.
 *
 * It reproduces the shape of a real 3-D Secure integration — redirect out, come back with
 * a signed callback, verify server-side — so the checkout, the order state machine and
 * the admin screens are exercised end to end without a merchant agreement. Swapping in a
 * real bank later changes this class only.
 *
 * The hosted page is served by the frontend at {@code /checkout/mock-payment}, which
 * offers "uğurlu" and "uğursuz" buttons that post back to our callback endpoint.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    private final AppProperties properties;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public InitiationResult initiate(InitiationRequest request) {
        String transactionId = "MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        // The signature the callback must carry back, computed the same way a real
        // provider would: over the fields that must not be tampered with.
        String signature = sign(request.reference(), transactionId, request.amount().toPlainString());

        String paymentUrl = "%s/checkout/mock-payment?reference=%s&transactionId=%s&amount=%s&currency=%s&signature=%s"
                .formatted(properties.getPublicUrl(),
                        urlEncode(request.reference()),
                        urlEncode(transactionId),
                        request.amount().toPlainString(),
                        urlEncode(request.currency()),
                        signature);

        log.info("[mock-payment] initiated {} for {} {}",
                transactionId, request.amount(), request.currency());

        Map<String, Object> raw = new HashMap<>();
        raw.put("provider", "mock");
        raw.put("transactionId", transactionId);
        raw.put("amount", request.amount());
        raw.put("currency", request.currency());
        raw.put("orderNumber", request.orderNumber());

        return InitiationResult.ok(paymentUrl, transactionId, raw);
    }

    @Override
    public CallbackResult handleCallback(Map<String, String> payload, String signatureHeader) {
        String reference = payload.get("reference");
        String transactionId = payload.get("transactionId");
        String amount = payload.get("amount");
        String status = payload.getOrDefault("status", "SUCCESS");
        String provided = signatureHeader != null ? signatureHeader : payload.get("signature");

        boolean valid = provided != null
                && constantTimeEquals(provided, sign(reference, transactionId, amount));

        if (!valid) {
            log.warn("[mock-payment] signature mismatch for reference {}", reference);
        }

        PaymentOutcome outcome = switch (status.toUpperCase()) {
            case "SUCCESS", "PAID", "APPROVED" -> PaymentOutcome.SUCCEEDED;
            case "CANCELLED", "CANCELED" -> PaymentOutcome.CANCELLED;
            case "DECLINED", "FAILED" -> PaymentOutcome.FAILED;
            case "PENDING" -> PaymentOutcome.PENDING;
            default -> PaymentOutcome.UNKNOWN;
        };

        return new CallbackResult(
                valid,
                reference,
                transactionId,
                // Provider event id: the transaction plus its outcome, so a redelivery of
                // the same event is recognised but a later refund event is not swallowed.
                transactionId + ":" + outcome.name(),
                outcome,
                amount == null ? null : new BigDecimal(amount),
                outcome == PaymentOutcome.SUCCEEDED ? "**** **** **** 4242" : null,
                outcome == PaymentOutcome.SUCCEEDED ? "VISA" : null,
                outcome == PaymentOutcome.FAILED ? "MOCK_DECLINED" : null,
                outcome == PaymentOutcome.FAILED ? "Test kartı imtina etdi" : null,
                new HashMap<>(payload));
    }

    @Override
    public VerificationResult verify(String reference, String externalTransactionId) {
        // A real provider is queried here. The mock reports what the callback already
        // recorded, so PaymentService treats verification as confirmation rather than
        // as a source of new state.
        log.debug("[mock-payment] verify {} / {}", reference, externalTransactionId);
        return new VerificationResult(PaymentOutcome.UNKNOWN, externalTransactionId, null, null,
                "Mock provayder üçün status yalnız callback ilə təyin olunur");
    }

    @Override
    public RefundResult refund(String reference, String externalTransactionId,
                               BigDecimal amount, String reason) {
        log.info("[mock-payment] refunded {} of {} ({})", amount, externalTransactionId, reason);
        return new RefundResult(true, amount,
                "MOCK-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), null);
    }

    // -----------------------------------------------------------------------

    private String sign(String reference, String transactionId, String amount) {
        String payload = String.join("|",
                nullSafe(reference), nullSafe(transactionId), nullSafe(amount),
                properties.getPayment().getWebhookSecret());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Length-independent comparison so signature checking leaks no timing information. */
    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(nullSafe(value), StandardCharsets.UTF_8);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
