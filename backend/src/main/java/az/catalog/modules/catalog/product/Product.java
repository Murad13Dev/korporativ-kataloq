package az.catalog.modules.catalog.product;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.domain.SoftDeletableEntity;
import az.catalog.modules.catalog.category.Category;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A catalog item.
 *
 * {@code price} is always the undiscounted list price. The sale price is never stored:
 * it is derived at read time by {@code DiscountResolver} from the campaigns in force,
 * which is what lets a campaign start and end on schedule without a batch job rewriting
 * product rows (and without corrupting the price history on past orders).
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "barcode", length = 80)
    private String barcode;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    // --- pricing ---------------------------------------------------------

    @Column(name = "price", precision = 14, scale = 2)
    private BigDecimal price;

    /** Manual "was" price shown when no campaign applies. Purely presentational. */
    @Column(name = "compare_at_price", precision = 14, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "AZN";

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    /** When false the storefront shows "Qiymət üçün müraciət edin" instead of a number. */
    @Column(name = "show_price", nullable = false)
    @Builder.Default
    private boolean showPrice = true;

    // --- commerce --------------------------------------------------------

    @Column(name = "purchasable", nullable = false)
    @Builder.Default
    private boolean purchasable = true;

    @Column(name = "track_stock", nullable = false)
    private boolean trackStock;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private int lowStockThreshold = 5;

    @Column(name = "allow_backorder", nullable = false)
    private boolean allowBackorder;

    @Column(name = "min_order_quantity", nullable = false)
    @Builder.Default
    private int minOrderQuantity = 1;

    @Column(name = "max_order_quantity")
    private Integer maxOrderQuantity;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    // --- presentation ----------------------------------------------------

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    // --- associations ----------------------------------------------------

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @MapKey(name = "languageCode")
    @Builder.Default
    private Map<String, ProductTranslation> translations = new LinkedHashMap<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<ProductDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<ProductAttributeValue> attributeValues = new ArrayList<>();

    // --- behaviour -------------------------------------------------------

    public ProductTranslation translation(String languageCode, String defaultLanguage) {
        ProductTranslation match = translations.get(languageCode);
        if (match != null) {
            return match;
        }
        ProductTranslation fallback = translations.get(defaultLanguage);
        return fallback != null ? fallback : translations.values().stream().findFirst().orElse(null);
    }

    public void putTranslation(String languageCode, ProductTranslation translation) {
        translation.setProduct(this);
        translation.setLanguageCode(languageCode);
        translations.put(languageCode, translation);
    }

    public ProductImage mainImage() {
        return images.stream()
                .filter(ProductImage::isMain)
                .findFirst()
                .orElseGet(() -> images.isEmpty() ? null : images.get(0));
    }

    public boolean isPublished() {
        return status == ContentStatus.ACTIVE && !isDeleted();
    }

    /** Whether the product can currently be added to a cart. */
    public boolean isAvailableForPurchase() {
        return isPublished() && purchasable && showPrice && price != null && inStock();
    }

    public boolean inStock() {
        return !trackStock || allowBackorder || stockQuantity > 0;
    }

    public boolean isLowStock() {
        return trackStock && stockQuantity > 0 && stockQuantity <= lowStockThreshold;
    }

    public StockStatus stockStatus() {
        if (!trackStock) {
            return StockStatus.IN_STOCK;
        }
        if (stockQuantity <= 0) {
            return allowBackorder ? StockStatus.BACKORDER : StockStatus.OUT_OF_STOCK;
        }
        return isLowStock() ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }

    public int availableQuantity() {
        return trackStock ? Math.max(stockQuantity, 0) : Integer.MAX_VALUE;
    }
}
