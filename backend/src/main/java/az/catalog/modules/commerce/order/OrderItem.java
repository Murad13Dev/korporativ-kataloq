package az.catalog.modules.commerce.order;

import az.catalog.modules.catalog.product.Product;
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

import java.math.BigDecimal;

/**
 * A frozen order line. {@code product} is nullable on purpose: the product may be deleted
 * later, and the snapshot below keeps the line complete without it.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_sku", length = 80)
    private String productSku;

    @Column(name = "product_slug", length = 180)
    private String productSlug;

    /**
     * Plain id, not a JPA relationship — the variant may be deleted after purchase, and
     * this line must stay complete without it (same reasoning as {@code product} being
     * nullable). Stock is reserved/released against this id directly.
     */
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "variant_sku", length = 80)
    private String variantSku;

    @Column(name = "variant_label", length = 255)
    private String variantLabel;

    @Column(name = "image_url", length = 700)
    private String imageUrl;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** List price at the moment of purchase. */
    @Column(name = "original_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalUnitPrice;

    /** Price actually charged per unit. */
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Which campaign produced the price, kept for reporting after the campaign ends. */
    @Column(name = "discount_name", length = 160)
    private String discountName;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice;

    public boolean wasDiscounted() {
        return discountAmount.signum() > 0;
    }
}
