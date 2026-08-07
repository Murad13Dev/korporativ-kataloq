package az.catalog.modules.commerce.payment;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

/**
 * Raw provider callback, stored before it is acted on.
 *
 * The unique index on {@code (provider, external_event_id)} is what makes webhook
 * redelivery a no-op instead of a double state transition — providers retry aggressively
 * and will happily deliver the same "payment succeeded" three times.
 */
@Entity
@Table(name = "payment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "external_event_id", length = 190)
    private String externalEventId;

    @Type(JsonBinaryType.class)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
