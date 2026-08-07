package az.catalog.modules.commerce.payment;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "Public · Ödəniş")
@RestController
@RequestMapping("/api/public/payments")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicPaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Ödəniş statusu",
            description = "Nəticə səhifəsi bunu çağırır. Callback hələ gəlməyibsə provayderdən birbaşa soruşulur.")
    @GetMapping("/{reference}/status")
    public DataResponse<PaymentService.PaymentStatusView> status(@PathVariable String reference) {
        return DataResponse.of(paymentService.status(reference));
    }

    @Operation(summary = "Ödənişi yenidən başlatmaq",
            description = "Ödəniş pəncərəsi hələ açıqdırsa sifariş üçün yeni ödəniş sessiyası yaradır.")
    @PostMapping("/retry/{orderId}")
    public DataResponse<PaymentService.PaymentSession> retry(@PathVariable Long orderId,
                                                             @RequestLanguage String language) {
        return DataResponse.of(paymentService.retry(orderId, language));
    }

    /**
     * Browser return from the provider. Kept separate from the webhook because a shopper's
     * browser is not a trustworthy channel — this only reports what the server already knows.
     */
    @Operation(summary = "Provayder callback-i (GET)")
    @GetMapping("/callback/{provider}")
    public ResponseEntity<Map<String, Object>> callbackGet(
            @PathVariable String provider,
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        return handle(provider, params, signature);
    }

    @Operation(summary = "Provayder callback-i / webhook (POST)",
            description = """
                    İdempotentdir: eyni hadisə təkrar göndərilsə ikinci dəfə emal edilmir.
                    İmzası yoxlanmayan callback saxlanılır, lakin heç bir statusu dəyişmir.
                    """)
    @PostMapping("/callback/{provider}")
    public ResponseEntity<Map<String, Object>> callbackPost(
            @PathVariable String provider,
            @RequestParam(required = false) Map<String, String> params,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        Map<String, String> merged = new HashMap<>();
        if (params != null) {
            merged.putAll(params);
        }
        if (body != null) {
            merged.putAll(body);
        }
        return handle(provider, merged, signature);
    }

    @Operation(summary = "Webhook alias", description = "Bəzi provayderlər /webhook yolunu tələb edir.")
    @PostMapping("/webhook/{provider}")
    public ResponseEntity<Map<String, Object>> webhook(
            @PathVariable String provider,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        return handle(provider, body == null ? Map.of() : body, signature);
    }

    /**
     * Stripe's own webhook. Its HMAC signature is computed over the exact raw request
     * bytes, so unlike every other provider here the body must reach the handler
     * unparsed — binding it to a {@code Map} first would both break the signature (key
     * order/whitespace no longer match) and fail outright on Stripe's nested JSON.
     */
    @Operation(summary = "Stripe webhook")
    @PostMapping("/stripe/webhook")
    public ResponseEntity<Map<String, Object>> stripeWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        try {
            PaymentService.CallbackOutcome outcome =
                    paymentService.handleRawCallback("stripe", rawBody, signature);
            return ResponseEntity.ok(Map.of(
                    "accepted", outcome.accepted(),
                    "result", outcome.result()));
        } catch (RuntimeException ex) {
            log.error("Failed to process stripe webhook", ex);
            return ResponseEntity.ok(Map.of("accepted", false, "result", "ERROR"));
        }
    }

    /**
     * Always answers 200. A provider that receives anything else retries the same event
     * indefinitely; problems are recorded on the payment_events row instead.
     */
    private ResponseEntity<Map<String, Object>> handle(String provider, Map<String, String> payload,
                                                       String signature) {
        try {
            PaymentService.CallbackOutcome outcome =
                    paymentService.handleCallback(provider, payload, signature);
            return ResponseEntity.ok(Map.of(
                    "accepted", outcome.accepted(),
                    "result", outcome.result()));
        } catch (RuntimeException ex) {
            log.error("Failed to process {} callback", provider, ex);
            return ResponseEntity.ok(Map.of("accepted", false, "result", "ERROR"));
        }
    }
}
