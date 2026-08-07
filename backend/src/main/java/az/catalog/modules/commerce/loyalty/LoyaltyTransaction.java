package az.catalog.modules.commerce.loyalty;

import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * One ledger row: a positive {@code points} for an award, negative for a redemption.
 * {@code orderId} is a plain id, not a relationship — the loyalty module has no reason
 * to depend on the order module's entity graph, only to remember which order paid for
 * an award (for display, and to stop double-crediting the same order).
 */
@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyTransaction {

    public enum Type { EARNED, REDEEMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    private int points;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private Type type;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
