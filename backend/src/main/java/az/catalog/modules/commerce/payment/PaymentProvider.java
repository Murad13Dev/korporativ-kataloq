package az.catalog.modules.commerce.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Contract every payment integration implements.
 *
 * The rest of the platform only ever sees this interface, so adding AzeriCard, Kapital
 * or PashaPay later means writing one class and flipping {@code app.payment.provider} —
 * no change to checkout, orders or the admin panel.
 *
 * <p>Implementations must be safe to call twice with the same {@code reference}: the
 * checkout may be retried, and providers do redeliver webhooks.
 */
public interface PaymentProvider {

    /** Value matched against {@code app.payment.provider}. */
    String name();

    /**
     * Registers the payment and returns where to send the shopper.
     *
     * @param request amount, currency, our reference and the return URL
     * @return the redirect target plus whatever the provider gave us to store
     */
    InitiationResult initiate(InitiationRequest request);

    /**
     * Interprets a callback or webhook body.
     *
     * @param payload    raw parameters as received
     * @param signature  provider signature header, when it sends one
     * @return what actually happened, including whether the signature verified
     */
    CallbackResult handleCallback(Map<String, String> payload, String signature);

    /**
     * Asks the provider for the authoritative status. Used when a shopper returns to the
     * site before the webhook lands, and by the reconciliation job.
     */
    VerificationResult verify(String reference, String externalTransactionId);

    /**
     * @param amount {@code null} refunds the full payment
     */
    RefundResult refund(String reference, String externalTransactionId, BigDecimal amount, String reason);

    // -----------------------------------------------------------------------

    record InitiationRequest(
            String reference,
            BigDecimal amount,
            String currency,
            String orderNumber,
            String description,
            String customerEmail,
            String customerName,
            String returnUrl,
            String callbackUrl,
            String language
    ) {
    }

    record InitiationResult(
            boolean success,
            /** Where the browser must be sent; null for a provider that needs no redirect. */
            String paymentUrl,
            String externalTransactionId,
            Map<String, Object> rawResponse,
            String failureCode,
            String failureMessage
    ) {
        public static InitiationResult ok(String paymentUrl, String externalTransactionId,
                                          Map<String, Object> raw) {
            return new InitiationResult(true, paymentUrl, externalTransactionId, raw, null, null);
        }

        public static InitiationResult failed(String code, String message) {
            return new InitiationResult(false, null, null, null, code, message);
        }
    }

    record CallbackResult(
            boolean signatureValid,
            String reference,
            String externalTransactionId,
            /** Provider-side unique event id; used to make redelivery idempotent. */
            String externalEventId,
            PaymentOutcome outcome,
            BigDecimal amount,
            String cardMask,
            String cardBrand,
            String failureCode,
            String failureMessage,
            Map<String, Object> rawPayload
    ) {
    }

    record VerificationResult(
            PaymentOutcome outcome,
            String externalTransactionId,
            BigDecimal amount,
            String cardMask,
            String failureMessage
    ) {
    }

    record RefundResult(
            boolean success,
            BigDecimal refundedAmount,
            String externalRefundId,
            String failureMessage
    ) {
    }

    enum PaymentOutcome {
        PENDING,
        AUTHORIZED,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        REFUNDED,
        UNKNOWN
    }
}
