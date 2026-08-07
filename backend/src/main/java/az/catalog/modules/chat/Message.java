package az.catalog.modules.chat;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    public enum SenderType {
        CUSTOMER, ADMIN, SYSTEM, BOT
    }

    public enum MessageType {
        TEXT, IMAGE, FILE, PRODUCT, ORDER, SYSTEM_EVENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 16)
    private SenderType senderType;

    /** users.id for ADMIN, customers.id for CUSTOMER, null for anonymous visitors. */
    @Column(name = "sender_id")
    private Long senderId;

    /** Snapshot, so a renamed or deleted operator does not rewrite past transcripts. */
    @Column(name = "sender_name", length = 160)
    private String senderName;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 24)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "media_id")
    private Long mediaId;

    /** Structured payload for PRODUCT / ORDER cards shared inside the thread. */
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public boolean isFromCustomer() {
        return senderType == SenderType.CUSTOMER;
    }
}
