package az.catalog.modules.commerce.promo;

import az.catalog.common.domain.ContentStatus;
import az.catalog.modules.catalog.discount.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class PromoCodeDtos {

    private PromoCodeDtos() {
    }

    public record PromoCodeResponse(
            Long id,
            String code,
            String description,
            DiscountType discountType,
            BigDecimal value,
            BigDecimal maxDiscountAmount,
            BigDecimal minSubtotal,
            boolean freeDelivery,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer usageLimit,
            int usageLimitPerCustomer,
            int usedCount,
            boolean excludeDiscounted,
            ContentStatus status,
            /** Derived from status + clock, so the list can show one honest state. */
            boolean running,
            boolean scheduled,
            boolean expired,
            boolean exhausted,
            OffsetDateTime createdAt
    ) {
        public static PromoCodeResponse from(PromoCode promo) {
            OffsetDateTime now = OffsetDateTime.now();
            boolean scheduled = promo.getStatus() == ContentStatus.ACTIVE
                    && promo.getStartsAt() != null && now.isBefore(promo.getStartsAt());
            boolean expired = promo.getEndsAt() != null && !now.isBefore(promo.getEndsAt());

            return new PromoCodeResponse(
                    promo.getId(), promo.getCode(), promo.getDescription(),
                    promo.getDiscountType(), promo.getValue(), promo.getMaxDiscountAmount(),
                    promo.getMinSubtotal(), promo.isFreeDelivery(),
                    promo.getStartsAt(), promo.getEndsAt(),
                    promo.getUsageLimit(), promo.getUsageLimitPerCustomer(), promo.getUsedCount(),
                    promo.isExcludeDiscounted(), promo.getStatus(),
                    promo.isRunning(now), scheduled, expired, !promo.hasCapacity(),
                    promo.getCreatedAt());
        }
    }

    public record SaveRequest(
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9_-]{3,48}",
                    message = "Kod yalnız hərf, rəqəm, tire və alt xətdən ibarət ola bilər")
            String code,

            @Size(max = 500) String description,

            @NotNull DiscountType discountType,
            @NotNull @DecimalMin(value = "0.00") BigDecimal value,
            @DecimalMin(value = "0.00") BigDecimal maxDiscountAmount,
            @DecimalMin(value = "0.00") BigDecimal minSubtotal,
            Boolean freeDelivery,

            OffsetDateTime startsAt,
            OffsetDateTime endsAt,

            @Min(1) Integer usageLimit,
            @Min(0) Integer usageLimitPerCustomer,

            Boolean excludeDiscounted,
            ContentStatus status
    ) {
    }

    public record StatusRequest(@NotNull ContentStatus status) {
    }

    /** Who used the code and when — the answer to "is this campaign working?". */
    public record RedemptionResponse(
            Long id,
            Long orderId,
            String orderNumber,
            Long customerId,
            String customerEmail,
            BigDecimal amount,
            OffsetDateTime createdAt
    ) {
    }
}
