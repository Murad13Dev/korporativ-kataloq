package az.catalog.modules.chat;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A support thread.
 *
 * {@code visitorToken} doubles as the channel key: it is the browser-generated secret
 * that both identifies an anonymous visitor across page loads and authorises their STOMP
 * subscription. It is always populated, including for logged-in customers, so the
 * subscription rule has no special cases.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends BaseEntity {

    public enum Status {
        OPEN, PENDING, RESOLVED, CLOSED;

        public String label() {
            return switch (this) {
                case OPEN -> "Açıq";
                case PENDING -> "Cavab gözləyir";
                case RESOLVED -> "Həll olunub";
                case CLOSED -> "Bağlanıb";
            };
        }
    }

    public enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Browser-held secret; also the STOMP channel key. */
    @Column(name = "visitor_token", length = 64)
    private String visitorToken;

    @Column(name = "visitor_name", length = 160)
    private String visitorName;

    @Column(name = "visitor_email", length = 190)
    private String visitorEmail;

    @Column(name = "visitor_phone", length = 40)
    private String visitorPhone;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(name = "locale", nullable = false, length = 8)
    @Builder.Default
    private String locale = "az";

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    // --- denormalised counters ---------------------------------------------
    //
    // The admin inbox lists dozens of threads at once; keeping the preview and the
    // unread counts on the row avoids a correlated subquery per line.

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    @Column(name = "unread_by_admin", nullable = false)
    private int unreadByAdmin;

    @Column(name = "unread_by_customer", nullable = false)
    private int unreadByCustomer;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    public boolean isClosed() {
        return status == Status.CLOSED;
    }

    public String displayName() {
        if (customer != null) {
            return customer.fullName();
        }
        return visitorName != null && !visitorName.isBlank() ? visitorName : "Qonaq";
    }

    public String contactEmail() {
        if (customer != null) {
            return customer.getEmail();
        }
        return visitorEmail;
    }
}
