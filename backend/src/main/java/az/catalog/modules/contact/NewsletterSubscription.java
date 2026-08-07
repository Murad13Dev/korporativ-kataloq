package az.catalog.modules.contact;

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

import java.time.OffsetDateTime;

/**
 * A newsletter signup.
 *
 * Double opt-in by default: a row starts PENDING and only becomes CONFIRMED once the
 * address owner clicks the link. Anyone can type someone else's address into a public
 * form, so confirming is what makes the list legitimate rather than a complaint queue.
 */
@Entity
@Table(name = "newsletter_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscription {

    public enum Status {
        PENDING, CONFIRMED, UNSUBSCRIBED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "name", length = 160)
    private String name;

    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.PENDING;

    /** SHA-256 hash of the opaque confirmation token; the raw value only goes by e-mail. */
    @Column(name = "confirm_token", length = 128)
    private String confirmToken;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "unsubscribed_at")
    private OffsetDateTime unsubscribedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
