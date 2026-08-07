package az.catalog.modules.commerce.cart;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One cart line.
 *
 * The prices here are a <em>snapshot</em> taken when the line was added. They are
 * re-resolved against live campaigns on every cart read, and any difference is reported
 * to the shopper explicitly rather than silently applied — a price that changes between
 * the cart page and the payment page is how trust in a store gets lost.
 */
@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Which colour/size (etc.) was picked, if the product has variants at all. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Price actually charged per unit at snapshot time (after campaign). */
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /** List price at snapshot time, for the strike-through. */
    @Column(name = "original_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(name = "discount_id")
    private Long discountId;

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal lineOriginalTotal() {
        return originalUnitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal lineSaving() {
        return lineOriginalTotal().subtract(lineTotal());
    }

    public boolean isDiscounted() {
        return originalUnitPrice.compareTo(unitPrice) > 0;
    }
}
