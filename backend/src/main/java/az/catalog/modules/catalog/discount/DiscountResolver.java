package az.catalog.modules.catalog.discount;

import az.catalog.modules.catalog.category.Category;
import az.catalog.modules.catalog.product.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns campaigns into prices.
 *
 * <p><b>Resolution rule.</b> Among the campaigns that are running and match the product,
 * the winner is chosen by, in order:
 * <ol>
 *   <li>highest {@code priority}</li>
 *   <li>then the most specific scope (PRODUCTS &gt; CATEGORIES &gt; ALL_PRODUCTS)</li>
 *   <li>then the largest saving for the customer</li>
 * </ol>
 * Campaigns never stack — a product carries exactly one price and one ribbon. Stacking
 * would make the displayed saving impossible to reason about and is the usual source of
 * "-120%" bugs in catalog systems.
 *
 * <p>Cart-level promo codes are separate and <em>do</em> combine with campaign prices;
 * that is handled by {@code PromoCodeService} against the already-discounted subtotal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountResolver {

    private final DiscountRepository discountRepository;

    /**
     * Snapshot of the campaigns in force, resolved once and reused for a whole page of
     * products. Callers that price more than one product must use this rather than
     * calling {@link #resolve(Product)} in a loop.
     */
    @Transactional(readOnly = true)
    public DiscountContext context() {
        OffsetDateTime now = OffsetDateTime.now();
        return new DiscountContext(discountRepository.findRunning(now), now);
    }

    @Transactional(readOnly = true)
    public PriceInfo resolve(Product product) {
        return context().priceFor(product);
    }

    /** Prices a batch with a single campaign lookup. */
    @Transactional(readOnly = true)
    public Map<Long, PriceInfo> resolveAll(Collection<Product> products) {
        DiscountContext context = context();
        Map<Long, PriceInfo> result = new HashMap<>(products.size());
        for (Product product : products) {
            result.put(product.getId(), context.priceFor(product));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Long> discountedProductIds() {
        return discountRepository.findDiscountedProductIds(OffsetDateTime.now());
    }

    /**
     * The set of running campaigns at one instant, plus the matching logic.
     * Immutable and safe to pass down through a request.
     */
    public record DiscountContext(List<Discount> running, OffsetDateTime at) {

        public PriceInfo priceFor(Product product) {
            String currency = product.getCurrency();
            BigDecimal listPrice = product.getPrice();

            // A product with no price, or with prices hidden, is never on campaign:
            // there is nothing meaningful to strike through.
            if (listPrice == null || listPrice.signum() <= 0 || !product.isShowPrice()) {
                return PriceInfo.noDiscount(listPrice, currency);
            }

            return bestFor(product)
                    .map(discount -> PriceInfo.of(discount, listPrice, currency))
                    .orElseGet(() -> PriceInfo.noDiscount(listPrice, currency));
        }

        public Optional<Discount> bestFor(Product product) {
            return running.stream()
                    .filter(discount -> matches(discount, product))
                    .max(Comparator
                            .comparingInt(Discount::getPriority)
                            .thenComparingInt(discount -> specificity(discount.getScope()))
                            .thenComparing(discount -> discount.savingFor(product.getPrice())));
        }

        private boolean matches(Discount discount, Product product) {
            return switch (discount.getScope()) {
                case ALL_PRODUCTS -> true;
                case PRODUCTS -> discount.getProductIds().contains(product.getId());
                case CATEGORIES -> matchesCategory(discount, product.getCategory());
            };
        }

        /**
         * Walks up the category chain so a campaign on "Mebel" also covers
         * "Mebel → Oturacaqlar". The materialised {@code path} means this is a
         * pure in-memory check with no extra queries.
         */
        private boolean matchesCategory(Discount discount, Category category) {
            if (category == null || discount.getCategoryIds().isEmpty()) {
                return false;
            }
            if (discount.getCategoryIds().contains(category.getId())) {
                return true;
            }
            String path = category.getPath();
            if (path == null || path.isBlank()) {
                return false;
            }
            for (String segment : path.split("/")) {
                if (!segment.isBlank() && discount.getCategoryIds().contains(Long.valueOf(segment))) {
                    return true;
                }
            }
            return false;
        }

        private static int specificity(DiscountScope scope) {
            return switch (scope) {
                case PRODUCTS -> 3;
                case CATEGORIES -> 2;
                case ALL_PRODUCTS -> 1;
            };
        }
    }
}
