package az.catalog.modules.catalog.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Everything the storefront needs to render a price and its ribbon, computed once per
 * product per request. Serialised straight onto product cards and detail responses.
 *
 * @param listPrice        undiscounted price from the product
 * @param finalPrice       what the customer actually pays per unit
 * @param savingAmount     {@code listPrice - finalPrice}
 * @param savingPercentage rounded percentage off, for the "-25%" ribbon
 * @param discounted       whether a campaign is in force right now
 * @param discountId       matched campaign, {@code null} when none applies
 * @param badgeLabel       ready-to-render ribbon text ("-25%", "BLACK FRIDAY")
 * @param endsAt           campaign end, drives the countdown when requested
 */
public record PriceInfo(
        BigDecimal listPrice,
        BigDecimal finalPrice,
        BigDecimal savingAmount,
        int savingPercentage,
        boolean discounted,
        Long discountId,
        String discountName,
        String badgeLabel,
        BadgeStyle badgeStyle,
        String badgeColor,
        boolean showBadge,
        boolean showCountdown,
        OffsetDateTime endsAt,
        String currency
) {

    /** No campaign matched: the customer pays the list price. */
    public static PriceInfo noDiscount(BigDecimal listPrice, String currency) {
        BigDecimal price = listPrice == null ? null : listPrice.setScale(2, RoundingMode.HALF_UP);
        return new PriceInfo(price, price, BigDecimal.ZERO, 0, false,
                null, null, null, null, null, false, false, null, currency);
    }

    public static PriceInfo of(Discount discount, BigDecimal listPrice, String currency) {
        BigDecimal saving = discount.savingFor(listPrice);
        if (saving.signum() <= 0) {
            return noDiscount(listPrice, currency);
        }
        BigDecimal list = listPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = list.subtract(saving).setScale(2, RoundingMode.HALF_UP);
        int percentage = discount.percentageFor(list);

        return new PriceInfo(
                list,
                finalPrice,
                saving,
                percentage,
                true,
                discount.getId(),
                discount.getName(),
                resolveBadgeLabel(discount, percentage, saving, currency),
                discount.getBadgeStyle(),
                discount.getBadgeColor(),
                discount.isShowBadge(),
                discount.isShowCountdown() && discount.getEndsAt() != null,
                discount.getEndsAt(),
                currency);
    }

    /**
     * An explicit campaign label wins; otherwise the ribbon shows the percentage, falling
     * back to the absolute amount for tiny savings that would round to "-0%".
     */
    private static String resolveBadgeLabel(Discount discount, int percentage,
                                            BigDecimal saving, String currency) {
        if (discount.getBadgeLabel() != null && !discount.getBadgeLabel().isBlank()) {
            return discount.getBadgeLabel();
        }
        if (percentage > 0) {
            return "-" + percentage + "%";
        }
        return "-" + saving.stripTrailingZeros().toPlainString() + " " + currency;
    }

    public BigDecimal totalFor(int quantity) {
        return finalPrice == null
                ? BigDecimal.ZERO
                : finalPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal savingFor(int quantity) {
        return savingAmount.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
