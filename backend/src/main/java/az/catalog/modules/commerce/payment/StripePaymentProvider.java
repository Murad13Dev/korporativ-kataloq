package az.catalog.modules.commerce.payment;

import az.catalog.common.config.AppProperties;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stripe Checkout — hosted payment page, PCI scope stays entirely with Stripe.
 *
 * <p><b>AZN caveat.</b> Stripe does not settle in Azerbaijani manat; {@code app.payment.provider=stripe}
 * only works when {@code app.commerce.default-currency} is one Stripe actually supports
 * (USD, EUR, GBP, …). For an AZN storefront, mock or a local bank gateway (Kapital, Payriff)
 * remains the real option — this provider is here for stores that sell internationally.
 *
 * <p>Needs a real Stripe account to do anything: {@code STRIPE_SECRET_KEY} /
 * {@code STRIPE_PUBLISHABLE_KEY} from a free test-mode account, and — once a webhook
 * endpoint is registered in the Stripe dashboard pointing at
 * {@code /api/public/payments/stripe/webhook} — {@code STRIPE_WEBHOOK_SECRET} from that
 * endpoint's settings. None of this is auto-provisioned; mock stays the default provider
 * until those three are set.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe")
@RequiredArgsConstructor
public class StripePaymentProvider implements PaymentProvider {

    /** Currencies Stripe expects as a whole-unit integer rather than *100 cents. */
    private static final Set<String> ZERO_DECIMAL_CURRENCIES =
            Set.of("JPY", "KRW", "VND", "CLP", "ISK", "HUF");

    private final AppProperties properties;

    @PostConstruct
    void init() {
        Stripe.apiKey = properties.getPayment().getStripe().getSecretKey();
        if (Stripe.apiKey == null || Stripe.apiKey.isBlank()) {
            log.warn("app.payment.provider=stripe but STRIPE_SECRET_KEY is empty — "
                    + "every call to Stripe will fail until it is set.");
        }
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public InitiationResult initiate(InitiationRequest request) {
        try {
            long unitAmount = toSmallestUnit(request.amount(), request.currency());

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setClientReferenceId(request.reference())
                    .setCustomerEmail(request.customerEmail())
                    .setSuccessUrl(request.returnUrl() + "&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(request.returnUrl() + "&cancelled=true")
                    .putMetadata("reference", request.reference())
                    .putMetadata("orderNumber", request.orderNumber())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(request.currency().toLowerCase())
                                    .setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(request.description() != null
                                                    ? request.description() : request.orderNumber())
                                            .build())
                                    .build())
                            .build())
                    .build();

            Session session = Session.create(params);

            Map<String, Object> raw = new HashMap<>();
            raw.put("provider", "stripe");
            raw.put("sessionId", session.getId());
            raw.put("orderNumber", request.orderNumber());

            log.info("[stripe] created checkout session {} for reference {}",
                    session.getId(), request.reference());

            return InitiationResult.ok(session.getUrl(), session.getId(), raw);
        } catch (StripeException ex) {
            log.error("[stripe] failed to create checkout session for reference {}",
                    request.reference(), ex);
            return InitiationResult.failed(ex.getCode(), ex.getMessage());
        }
    }

    @Override
    public CallbackResult handleRawCallback(String rawBody, String signature) {
        String webhookSecret = properties.getPayment().getStripe().getWebhookSecret();
        Event event;
        try {
            event = Webhook.constructEvent(rawBody, signature, webhookSecret);
        } catch (SignatureVerificationException | IllegalArgumentException ex) {
            log.warn("[stripe] webhook signature verification failed: {}", ex.getMessage());
            return new CallbackResult(false, null, null, null,
                    PaymentOutcome.UNKNOWN, null, null, null, null, null, Map.of());
        }

        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        String reference = session != null ? session.getClientReferenceId() : null;
        String externalId = session != null ? session.getPaymentIntent() : null;
        BigDecimal amount = session != null && session.getAmountTotal() != null
                ? fromSmallestUnit(session.getAmountTotal(), session.getCurrency())
                : null;

        PaymentOutcome outcome = switch (event.getType()) {
            case "checkout.session.completed" -> "paid".equals(session != null ? session.getPaymentStatus() : null)
                    ? PaymentOutcome.SUCCEEDED : PaymentOutcome.PENDING;
            case "checkout.session.expired" -> PaymentOutcome.CANCELLED;
            case "checkout.session.async_payment_failed" -> PaymentOutcome.FAILED;
            case "charge.refunded" -> PaymentOutcome.REFUNDED;
            default -> PaymentOutcome.UNKNOWN;
        };

        return new CallbackResult(
                true,
                reference,
                externalId,
                event.getId(),
                outcome,
                amount,
                null, // Stripe Checkout does not expose the card mask on the session object.
                null,
                outcome == PaymentOutcome.FAILED ? "payment_failed" : null,
                outcome == PaymentOutcome.FAILED ? "Stripe ödənişi uğursuz oldu" : null,
                Map.of("eventType", event.getType(), "eventId", event.getId()));
    }

    @Override
    public CallbackResult handleCallback(Map<String, String> payload, String signature) {
        // Stripe only ever calls the raw-body webhook; nothing reaches this path.
        return new CallbackResult(false, null, null, null, PaymentOutcome.UNKNOWN,
                null, null, null, null, null, Map.of());
    }

    @Override
    public VerificationResult verify(String reference, String externalTransactionId) {
        try {
            Session session = Session.retrieve(externalTransactionId);
            PaymentOutcome outcome = "paid".equals(session.getPaymentStatus())
                    ? PaymentOutcome.SUCCEEDED : PaymentOutcome.PENDING;
            BigDecimal amount = session.getAmountTotal() != null
                    ? fromSmallestUnit(session.getAmountTotal(), session.getCurrency()) : null;
            return new VerificationResult(outcome, session.getPaymentIntent(), amount, null, null);
        } catch (StripeException ex) {
            log.debug("[stripe] verify failed for {}: {}", externalTransactionId, ex.getMessage());
            return new VerificationResult(PaymentOutcome.UNKNOWN, externalTransactionId, null, null,
                    ex.getMessage());
        }
    }

    @Override
    public RefundResult refund(String reference, String externalTransactionId, BigDecimal amount, String reason) {
        try {
            Session session = Session.retrieve(externalTransactionId);
            RefundCreateParams.Builder builder = RefundCreateParams.builder()
                    .setPaymentIntent(session.getPaymentIntent());
            if (amount != null) {
                builder.setAmount(toSmallestUnit(amount, session.getCurrency().toUpperCase()));
            }
            Refund refund = Refund.create(builder.build());
            BigDecimal refunded = fromSmallestUnit(refund.getAmount(), refund.getCurrency());
            return new RefundResult(true, refunded, refund.getId(), null);
        } catch (StripeException ex) {
            log.error("[stripe] refund failed for {}", externalTransactionId, ex);
            return new RefundResult(false, null, null, ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------

    private long toSmallestUnit(BigDecimal amount, String currency) {
        if (ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase())) {
            return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private BigDecimal fromSmallestUnit(long amount, String currency) {
        if (ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase())) {
            return BigDecimal.valueOf(amount);
        }
        return BigDecimal.valueOf(amount).movePointLeft(2);
    }
}
