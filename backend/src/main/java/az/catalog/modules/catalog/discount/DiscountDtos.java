package az.catalog.modules.catalog.discount;

import az.catalog.common.domain.ContentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class DiscountDtos {

    private DiscountDtos() {
    }

    /** Admin list row. {@code productCount} is the number of products currently matched. */
    public record DiscountSummary(
            Long id,
            String name,
            DiscountType discountType,
            BigDecimal value,
            DiscountScope scope,
            ContentStatus status,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int priority,
            BadgeStyle badgeStyle,
            String badgeLabel,
            boolean running,
            boolean scheduled,
            boolean expired,
            int targetCount
    ) {
        public static DiscountSummary from(Discount discount) {
            OffsetDateTime now = OffsetDateTime.now();
            int targets = switch (discount.getScope()) {
                case PRODUCTS -> discount.getProductIds().size();
                case CATEGORIES -> discount.getCategoryIds().size();
                case ALL_PRODUCTS -> 0;
            };
            return new DiscountSummary(
                    discount.getId(), discount.getName(), discount.getDiscountType(), discount.getValue(),
                    discount.getScope(), discount.getStatus(), discount.getStartsAt(), discount.getEndsAt(),
                    discount.getPriority(), discount.getBadgeStyle(), discount.getBadgeLabel(),
                    discount.isRunning(now), discount.isScheduled(now), discount.hasExpired(now), targets);
        }
    }

    public record DiscountResponse(
            Long id,
            String name,
            String description,
            DiscountType discountType,
            BigDecimal value,
            BigDecimal maxDiscountAmount,
            DiscountScope scope,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int priority,
            boolean showBadge,
            String badgeLabel,
            BadgeStyle badgeStyle,
            String badgeColor,
            boolean showCountdown,
            ContentStatus status,
            Set<Long> productIds,
            Set<Long> categoryIds,
            boolean running,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static DiscountResponse from(Discount discount) {
            return new DiscountResponse(
                    discount.getId(), discount.getName(), discount.getDescription(),
                    discount.getDiscountType(), discount.getValue(), discount.getMaxDiscountAmount(),
                    discount.getScope(), discount.getStartsAt(), discount.getEndsAt(), discount.getPriority(),
                    discount.isShowBadge(), discount.getBadgeLabel(), discount.getBadgeStyle(),
                    discount.getBadgeColor(), discount.isShowCountdown(), discount.getStatus(),
                    discount.getProductIds(), discount.getCategoryIds(),
                    discount.isRunning(OffsetDateTime.now()),
                    discount.getCreatedAt(), discount.getUpdatedAt());
        }
    }

    public record SaveRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,

            @NotNull DiscountType discountType,
            @NotNull @DecimalMin(value = "0.01", message = "Endirim dəyəri 0-dan böyük olmalıdır")
            BigDecimal value,
            @DecimalMin(value = "0.00") BigDecimal maxDiscountAmount,

            @NotNull DiscountScope scope,
            Set<Long> productIds,
            Set<Long> categoryIds,

            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer priority,

            Boolean showBadge,
            @Size(max = 48) String badgeLabel,
            BadgeStyle badgeStyle,
            @Pattern(regexp = "#[0-9a-fA-F]{3,8}", message = "Rəng #RRGGBB formatında olmalıdır")
            String badgeColor,
            Boolean showCountdown,

            ContentStatus status
    ) {
    }

    public record StatusRequest(@NotNull ContentStatus status) {
    }

    /**
     * Lets the admin see what a campaign would do before switching it on:
     * how many products it touches and the resulting price for a sample of them.
     */
    public record PreviewResponse(
            int affectedProductCount,
            BigDecimal totalListValue,
            BigDecimal totalDiscountedValue,
            List<PreviewLine> sample
    ) {
    }

    public record PreviewLine(
            Long productId,
            String sku,
            String name,
            BigDecimal listPrice,
            BigDecimal finalPrice,
            BigDecimal saving,
            int savingPercentage,
            String badgeLabel
    ) {
    }
}
