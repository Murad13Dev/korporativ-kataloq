package az.catalog.modules.notification.push;

import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** One browser/device's push registration. {@code endpoint} alone identifies it — the
    same shopper on two devices (or two browsers) is two rows, and re-subscribing the
    same device is an upsert on that same endpoint, not a duplicate. */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "endpoint", nullable = false, length = 700)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 200)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 100)
    private String authKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
