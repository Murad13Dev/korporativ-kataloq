package az.catalog.modules.catalog.discount;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.domain.SoftDeletableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A scheduled discount campaign.
 *
 * Campaigns are the only source of sale prices in the system. Nothing writes a
 * discounted number back onto {@link az.catalog.modules.catalog.product.Product},
 * so a campaign ending is simply a campaign no longer matching — no cleanup job,
 * no risk of a product being stranded at a promotional price.
 */
@Entity
@Table(name = "discounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 24)
    private DiscountType discountType;

    /** Percent (1–100) for PERCENTAGE, absolute currency amount for FIXED_AMOUNT. */
    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    /** Caps a percentage campaign, e.g. "30% off, at most 50 ₼". */
    @Column(name = "max_discount_amount", precision = 14, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 24)
    @Builder.Default
    private DiscountScope scope = DiscountScope.PRODUCTS;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    /** Higher priority wins when several campaigns match one product. */
    @Column(name = "priority", nullable = false)
    private int priority;

    // --- storefront ribbon ------------------------------------------------

    @Column(name = "show_badge", nullable = false)
    @Builder.Default
    private boolean showBadge = true;

    /** {@code null} renders the automatic label ("-25%"). */
    @Column(name = "badge_label", length = 48)
    private String badgeLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_style", nullable = false, length = 24)
    @Builder.Default
    private BadgeStyle badgeStyle = BadgeStyle.SALE;

    @Column(name = "badge_color", length = 16)
    private String badgeColor;

    @Column(name = "show_countdown", nullable = false)
    private boolean showCountdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    // --- targeting --------------------------------------------------------

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "discount_products", joinColumns = @JoinColumn(name = "discount_id"))
    @Column(name = "product_id")
    @Builder.Default
    private Set<Long> productIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "discount_categories", joinColumns = @JoinColumn(name = "discount_id"))
    @Column(name = "category_id")
    @Builder.Default
    private Set<Long> categoryIds = new LinkedHashSet<>();

    // --- behaviour --------------------------------------------------------

    /** True when the campaign is switched on and the current instant falls in its window. */
    public boolean isRunning(OffsetDateTime at) {
        if (status != ContentStatus.ACTIVE || isDeleted()) {
            return false;
        }
        if (startsAt != null && at.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || at.isBefore(endsAt);
    }

    /** A campaign that is switched on but has not started yet. */
    public boolean isScheduled(OffsetDateTime at) {
        return status == ContentStatus.ACTIVE && !isDeleted()
                && startsAt != null && at.isBefore(startsAt);
    }

    public boolean hasExpired(OffsetDateTime at) {
        return endsAt != null && !at.isBefore(endsAt);
    }

    /**
     * Money taken off one unit at {@code listPrice}. Never exceeds the list price itself,
     * so a mis-configured fixed-amount campaign can never produce a negative price.
     */
    public BigDecimal savingFor(BigDecimal listPrice) {
        if (listPrice == null || listPrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal saving = discountType == DiscountType.PERCENTAGE
                ? listPrice.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : value;

        if (maxDiscountAmount != null && saving.compareTo(maxDiscountAmount) > 0) {
            saving = maxDiscountAmount;
        }
        if (saving.compareTo(listPrice) > 0) {
            saving = listPrice;
        }
        return saving.setScale(2, RoundingMode.HALF_UP);
    }

    /** Effective percentage off, used for the "-25%" ribbon regardless of campaign type. */
    public int percentageFor(BigDecimal listPrice) {
        BigDecimal saving = savingFor(listPrice);
        if (listPrice == null || listPrice.signum() <= 0 || saving.signum() <= 0) {
            return 0;
        }
        return saving.multiply(BigDecimal.valueOf(100))
                .divide(listPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
