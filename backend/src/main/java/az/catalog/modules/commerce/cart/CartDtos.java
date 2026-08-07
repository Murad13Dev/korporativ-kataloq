package az.catalog.modules.commerce.cart;

import az.catalog.modules.catalog.product.StockStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record CartResponse(
            Long id,
            String sessionId,
            String currency,
            List<CartLine> items,
            CartTotals totals,
            AppliedPromo promo,
            /**
             * Non-fatal problems found while re-pricing: a campaign started or ended, a
             * product went out of stock, quantity was clamped. Rendered as banners rather
             * than blocking the cart.
             */
            List<CartNotice> notices,
            boolean checkoutAllowed
    ) {
        public static CartResponse empty(String currency, String sessionId) {
            return new CartResponse(null, sessionId, currency, List.of(),
                    CartTotals.zero(currency), null, List.of(), false);
        }
    }

    public record CartLine(
            Long id,
            Long productId,
            String slug,
            String sku,
            String name,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal originalUnitPrice,
            BigDecimal lineTotal,
            BigDecimal lineSaving,
            boolean discounted,
            String discountName,
            String badgeLabel,
            int savingPercentage,
            StockStatus stockStatus,
            Integer availableQuantity,
            int minOrderQuantity,
            Integer maxOrderQuantity,
            boolean available,
            Long variantId,
            String variantSku,
            String variantLabel
    ) {
    }

    /**
     * @param itemDiscountTotal saving from product campaigns
     * @param promoDiscount     saving from the cart-level voucher
     * @param totalSaving       the two combined, for the "siz qənaət etdiniz" line
     */
    public record CartTotals(
            BigDecimal originalSubtotal,
            BigDecimal subtotal,
            BigDecimal itemDiscountTotal,
            BigDecimal promoDiscount,
            BigDecimal totalSaving,
            BigDecimal deliveryPrice,
            BigDecimal total,
            String currency,
            int itemCount,
            int totalQuantity,
            boolean freeDelivery,
            /** How much more is needed to unlock free delivery; null when already free. */
            BigDecimal amountToFreeDelivery
    ) {
        public static CartTotals zero(String currency) {
            return new CartTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, currency, 0, 0, false, null);
        }
    }

    public record AppliedPromo(
            String code,
            String description,
            BigDecimal amount,
            boolean freeDelivery
    ) {
    }

    public record CartNotice(String type, String productName, String message) {
    }

    // --- requests ---

    public record AddItemRequest(
            @NotNull Long productId,
            @Min(1) Integer quantity,
            /** Required when the product has variants; ignored otherwise. */
            Long variantId
    ) {
        public int quantityOrOne() {
            return quantity == null ? 1 : quantity;
        }
    }

    public record UpdateItemRequest(@NotNull @Min(0) Integer quantity) {
    }

    public record PromoCodeRequest(@NotBlank @Size(max = 48) String code) {
    }
}
