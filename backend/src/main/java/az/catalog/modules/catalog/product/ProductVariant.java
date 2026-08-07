package az.catalog.modules.catalog.product;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.catalog.attribute.AttributeOption;
import az.catalog.modules.media.MediaFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A purchasable refinement of a product — a colour, a size, or a colour+size pair —
 * with its own SKU and stock count, and an optional price override.
 *
 * Deliberately not a child of {@code Product}'s own association graph (no {@code List}
 * added there): this session's EntityGraph work found that combining two collections in
 * one fetch fans a query out into duplicate rows, and {@code Product} already carries
 * {@code images}/{@code translations}/{@code attributeValues}. Variants are looked up
 * through {@link ProductVariantRepository} instead, kept entirely separate.
 */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    /** Replaces the product's resolved price outright when set — no campaign math re-applied. */
    @Column(name = "price_override", precision = 14, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "track_stock", nullable = false)
    @Builder.Default
    private boolean trackStock = true;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "allow_backorder", nullable = false)
    private boolean allowBackorder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_media_id")
    private MediaFile image;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_variant_options",
            joinColumns = @JoinColumn(name = "variant_id"),
            inverseJoinColumns = @JoinColumn(name = "attribute_option_id"))
    @Builder.Default
    private Set<AttributeOption> options = new LinkedHashSet<>();

    public boolean inStock() {
        return !trackStock || allowBackorder || stockQuantity > 0;
    }

    public boolean isLowStock(int threshold) {
        return trackStock && stockQuantity > 0 && stockQuantity <= threshold;
    }

    public StockStatus stockStatus(int lowStockThreshold) {
        if (!trackStock) {
            return StockStatus.IN_STOCK;
        }
        if (stockQuantity <= 0) {
            return allowBackorder ? StockStatus.BACKORDER : StockStatus.OUT_OF_STOCK;
        }
        return isLowStock(lowStockThreshold) ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }

    public int availableQuantity() {
        return trackStock ? Math.max(stockQuantity, 0) : Integer.MAX_VALUE;
    }

    /** "Qırmızı / M" — options in a stable order for display and for matching a picked combination. */
    public String optionLabel(String languageCode, String defaultLanguage) {
        return options.stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(option -> option.label(languageCode, defaultLanguage))
                .reduce((a, b) -> a + " / " + b)
                .orElse(sku);
    }
}
