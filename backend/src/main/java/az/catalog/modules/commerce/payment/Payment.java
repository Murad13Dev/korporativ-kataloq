package az.catalog.modules.commerce.payment;

import az.catalog.common.domain.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    public enum Status {
        CREATED, PENDING, AUTHORIZED, SUCCEEDED, FAILED, CANCELLED,
        REFUNDED, PARTIALLY_REFUNDED, EXPIRED
    }

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    /** Our reference sent to the provider; also the idempotency key for retries. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "external_transaction_id", length = 190)
    private String externalTransactionId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "AZN";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.CREATED;

    @Column(name = "payment_url", length = 1000)
    private String paymentUrl;

    @Column(name = "card_mask", length = 32)
    private String cardMask;

    @Column(name = "card_brand", length = 32)
    private String cardBrand;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Type(JsonBinaryType.class)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private JsonNode requestPayload;

    @Type(JsonBinaryType.class)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private JsonNode responsePayload;

    @Type(JsonBinaryType.class)
    @Column(name = "callback_payload", columnDefinition = "jsonb")
    private JsonNode callbackPayload;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public boolean isFinal() {
        return status == Status.SUCCEEDED || status == Status.FAILED
                || status == Status.CANCELLED || status == Status.REFUNDED
                || status == Status.EXPIRED;
    }

    public BigDecimal refundableAmount() {
        return amount.subtract(refundedAmount).max(BigDecimal.ZERO);
    }
}
