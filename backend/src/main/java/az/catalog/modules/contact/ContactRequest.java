package az.catalog.modules.contact;

import az.catalog.common.domain.BaseEntity;
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

import java.time.OffsetDateTime;

/**
 * An inbound lead: the contact form, a product inquiry, or a quote request.
 *
 * These are the only rows in the system a complete stranger can create, so everything
 * about them is defensive — the text is stripped on write, the IP is recorded, and the
 * public endpoint behind them is rate limited.
 */
@Entity
@Table(name = "contact_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactRequest extends BaseEntity {

    public enum Status {
        NEW, IN_PROGRESS, ANSWERED, CLOSED, SPAM;

        public String label() {
            return switch (this) {
                case NEW -> "Yeni";
                case IN_PROGRESS -> "İşlənir";
                case ANSWERED -> "Cavablandırılıb";
                case CLOSED -> "Bağlanıb";
                case SPAM -> "Spam";
            };
        }
    }

    public enum RequestType {
        CONTACT, PRODUCT_INQUIRY, QUOTE, SUPPORT, PARTNERSHIP;

        public String label() {
            return switch (this) {
                case CONTACT -> "Əlaqə";
                case PRODUCT_INQUIRY -> "Məhsul sorğusu";
                case QUOTE -> "Qiymət təklifi";
                case SUPPORT -> "Dəstək";
                case PARTNERSHIP -> "Əməkdaşlıq";
            };
        }
    }

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "subject", length = 190)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 24)
    @Builder.Default
    private RequestType requestType = RequestType.CONTACT;

    /** Set when the lead came from a product page. */
    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.NEW;

    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private OffsetDateTime handledAt;

    @Column(name = "privacy_accepted", nullable = false)
    private boolean privacyAccepted;

    /** Which page the form was submitted from — useful for spotting a broken form. */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public boolean isOpen() {
        return status == Status.NEW || status == Status.IN_PROGRESS;
    }
}
