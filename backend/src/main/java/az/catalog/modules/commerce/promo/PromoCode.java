package az.catalog.modules.commerce.promo;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.domain.SoftDeletableEntity;
import az.catalog.modules.catalog.discount.DiscountType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * A cart-level voucher the shopper types at checkout.
 *
 * Unlike a {@link az.catalog.modules.catalog.discount.Discount} campaign — which sets the
 * price of individual products — a promo code reduces the cart total. The two <em>do</em>
 * combine: the code applies to the subtotal that campaigns have already produced, unless
 * {@code excludeDiscounted} says otherwise.
 */
@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode extends SoftDeletableEntity {

    @Column(name = "code", nullable = false, length = 48)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 24)
    private DiscountType discountType;

    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(name = "max_discount_amount", precision = 14, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_subtotal", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal minSubtotal = BigDecimal.ZERO;

    /** Zeroes the delivery fee instead of (or as well as) reducing the subtotal. */
    @Column(name = "free_delivery", nullable = false)
    private boolean freeDelivery;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_limit_per_customer", nullable = false)
    @Builder.Default
    private int usageLimitPerCustomer = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** When true the code ignores lines that are already on a campaign. */
    @Column(name = "exclude_discounted", nullable = false)
    private boolean excludeDiscounted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;

    public boolean isRunning(OffsetDateTime at) {
        if (status != ContentStatus.ACTIVE || isDeleted()) {
            return false;
        }
        if (startsAt != null && at.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || at.isBefore(endsAt);
    }

    public boolean hasCapacity() {
        return usageLimit == null || usedCount < usageLimit;
    }

    /** Money off the given subtotal, capped and never larger than the subtotal itself. */
    public BigDecimal discountFor(BigDecimal subtotal) {
        if (freeDelivery && value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (subtotal == null || subtotal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = discountType == DiscountType.PERCENTAGE
                ? subtotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : value;

        if (maxDiscountAmount != null && amount.compareTo(maxDiscountAmount) > 0) {
            amount = maxDiscountAmount;
        }
        if (amount.compareTo(subtotal) > 0) {
            amount = subtotal;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
