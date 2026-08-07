package az.catalog.modules.commerce.cart;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shopping cart, owned either by a logged-in customer or by an anonymous session token
 * held in the browser. On login the guest cart is merged into the customer's cart.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends BaseEntity {

    public enum Status {
        ACTIVE, CONVERTED, ABANDONED
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Client-generated opaque token for anonymous carts. */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "AZN";

    @Column(name = "promo_code_id")
    private Long promoCodeId;

    @Column(name = "promo_code", length = 48)
    private String promoCode;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /**
     * A line is identified by product *and* variant — two different variants of the same
     * product are different lines, but two calls with {@code variantId == null} still
     * collapse onto the same plain-product line.
     */
    public Optional<CartItem> findItem(Long productId, Long variantId) {
        return items.stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && Objects.equals(item.getVariant() == null ? null : item.getVariant().getId(), variantId))
                .findFirst();
    }

    public int totalQuantity() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clearPromoCode() {
        this.promoCodeId = null;
        this.promoCode = null;
    }
}
