package az.catalog.modules.commerce.cart;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.discount.DiscountResolver;
import az.catalog.modules.catalog.discount.PriceInfo;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.catalog.product.ProductVariant;
import az.catalog.modules.catalog.product.ProductVariantRepository;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.commerce.promo.PromoCode;
import az.catalog.modules.commerce.promo.PromoCodeService;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.notification.MailService;
import az.catalog.modules.siteconfig.SiteConfigSnapshot;
import az.catalog.modules.siteconfig.SiteConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cart lifecycle and pricing.
 *
 * <p>Every read re-resolves prices against the live campaigns. When a stored line price
 * no longer matches, the line is corrected <em>and</em> a notice is returned, so the
 * shopper is told "bu məhsulun qiyməti dəyişdi" rather than discovering it on the payment
 * page. That is also what makes the checkout's price re-validation a formality instead of
 * a surprise rejection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_LINES = 100;
    private static final int MAX_QUANTITY_PER_LINE = 999;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final DiscountResolver discountResolver;
    private final PromoCodeService promoCodeService;
    private final LanguageService languageService;
    private final SiteConfigurationService siteConfigurationService;
    private final MailService mailService;
    private final AppProperties properties;

    // =======================================================================
    // Reads
    // =======================================================================

    @Transactional
    public CartDtos.CartResponse getCart(CartOwner owner, String language) {
        requireCommerceEnabled();
        return findCart(owner)
                .map(cart -> price(cart, language))
                .orElseGet(() -> CartDtos.CartResponse.empty(currency(), owner.sessionId()));
    }

    @Transactional(readOnly = true)
    public int itemCount(CartOwner owner) {
        return findCart(owner).map(Cart::totalQuantity).orElse(0);
    }

    // =======================================================================
    // Mutations
    // =======================================================================

    @Transactional
    public CartDtos.CartResponse addItem(CartOwner owner, CartDtos.AddItemRequest request, String language) {
        requireCommerceEnabled();

        Product product = productRepository.findAliveById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Məhsul", request.productId()));
        requirePurchasable(product);

        ProductVariant variant = resolveVariant(product, request.variantId());

        Cart cart = findCart(owner).orElseGet(() -> createCart(owner));
        if (cart.getItems().size() >= MAX_LINES) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "Səbətdə maksimum %d fərqli məhsul ola bilər".formatted(MAX_LINES));
        }

        int requested = request.quantityOrOne();
        LinePrice price = resolvePrice(product, variant);

        Optional<CartItem> existing = cart.findItem(product.getId(), variant == null ? null : variant.getId());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(clampQuantity(product, variant, item.getQuantity() + requested));
            item.setUnitPrice(price.unitPrice());
            item.setOriginalUnitPrice(price.originalUnitPrice());
            item.setDiscountId(price.discountId());
        } else {
            int quantity = clampQuantity(product, variant, Math.max(requested, product.getMinOrderQuantity()));
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .quantity(quantity)
                    .unitPrice(price.unitPrice())
                    .originalUnitPrice(price.originalUnitPrice())
                    .discountId(price.discountId())
                    .build());
        }

        cartRepository.save(cart);
        return price(cart, language);
    }

    /**
     * Shared engine behind "reorder a past order" and "B2B bulk import" — both are really
     * the same operation, add several known products at once, and neither can be allowed
     * to fail as a unit: one discontinued SKU in a 40-line purchase order must not block
     * the other 39. Each line is attempted independently through the same validation
     * {@link #addItem} already applies (stock, variant requirement, purchasability); a
     * failure is recorded as a skip message instead of aborting the batch.
     */
    @Transactional
    public CartDtos.BulkAddResponse bulkAdd(CartOwner owner, List<CartDtos.BulkLine> lines, String language) {
        requireCommerceEnabled();
        int added = 0;
        List<String> skipped = new ArrayList<>();

        for (CartDtos.BulkLine line : lines) {
            try {
                addItem(owner, new CartDtos.AddItemRequest(line.productId(), line.quantity(), line.variantId()), language);
                added++;
            } catch (ApiException ex) {
                skipped.add(line.reference() + ": " + ex.getMessage());
            }
        }

        return new CartDtos.BulkAddResponse(getCart(owner, language), added, skipped);
    }

    /**
     * The B2B path: a shopper pastes or uploads a SKU + quantity list, and each row is
     * resolved to a product here before falling into the same {@link #bulkAdd} engine an
     * order reorder uses. An unknown SKU is reported by the text the shopper typed, since
     * there is no product id yet to reference.
     */
    @Transactional
    public CartDtos.BulkAddResponse bulkImportBySku(CartOwner owner, List<CartDtos.SkuLine> requested, String language) {
        List<CartDtos.BulkLine> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        for (CartDtos.SkuLine row : requested) {
            String sku = row.sku().trim();
            productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull(sku)
                    .ifPresentOrElse(
                            product -> resolved.add(new CartDtos.BulkLine(product.getId(), null, row.quantity(), sku)),
                            () -> unresolved.add(sku + ": SKU tapılmadı"));
        }

        CartDtos.BulkAddResponse result = bulkAdd(owner, resolved, language);
        if (unresolved.isEmpty()) {
            return result;
        }
        List<String> allSkipped = new ArrayList<>(result.skipped());
        allSkipped.addAll(unresolved);
        return new CartDtos.BulkAddResponse(result.cart(), result.addedCount(), allSkipped);
    }

    /**
     * A product with active variants cannot be added "bare" — the shopper must have
     * picked one, or the line would be ambiguous about which SKU/stock it draws from.
     */
    private ProductVariant resolveVariant(Product product, Long variantId) {
        if (variantId == null) {
            if (variantRepository.countByProductIdAndActiveTrue(product.getId()) > 0) {
                throw ApiException.field(ErrorCode.VALIDATION_ERROR, "variantId",
                        "Bu məhsul üçün variant seçilməlidir");
            }
            return null;
        }
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, product.getId())
                .orElseThrow(() -> ApiException.notFound("Variant", variantId));
        if (!variant.isActive()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        if (!variant.inStock()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);
        }
        return variant;
    }

    /** A price override replaces the resolved price outright — no campaign math re-applied. */
    private LinePrice resolvePrice(Product product, ProductVariant variant) {
        if (variant != null && variant.getPriceOverride() != null) {
            return new LinePrice(variant.getPriceOverride(), variant.getPriceOverride(), null);
        }
        PriceInfo price = discountResolver.resolve(product);
        return new LinePrice(price.finalPrice(), price.listPrice(), price.discountId());
    }

    private record LinePrice(BigDecimal unitPrice, BigDecimal originalUnitPrice, Long discountId) {
    }

    /** Quantity 0 removes the line, which is what the "−" button does at quantity 1. */
    @Transactional
    public CartDtos.CartResponse updateItem(CartOwner owner, Long itemId,
                                            CartDtos.UpdateItemRequest request, String language) {
        Cart cart = requireCart(owner);
        CartItem item = cart.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Səbət sətri", itemId));

        if (request.quantity() == 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(clampQuantity(item.getProduct(), item.getVariant(), request.quantity()));
        }
        return price(cart, language);
    }

    @Transactional
    public CartDtos.CartResponse removeItem(CartOwner owner, Long itemId, String language) {
        Cart cart = requireCart(owner);
        cart.getItems().removeIf(item -> item.getId().equals(itemId));
        return price(cart, language);
    }

    @Transactional
    public CartDtos.CartResponse clear(CartOwner owner, String language) {
        Cart cart = requireCart(owner);
        cart.getItems().clear();
        cart.clearPromoCode();
        return price(cart, language);
    }

    // --- promo code -------------------------------------------------------

    @Transactional
    public CartDtos.CartResponse applyPromoCode(CartOwner owner, String code, String language) {
        Cart cart = requireCart(owner);
        if (cart.isEmpty()) {
            throw new ApiException(ErrorCode.CART_EMPTY);
        }

        Totals totals = computeTotals(cart, null, null);
        String email = owner.customerId() == null ? null
                : customerRepository.findAliveById(owner.customerId()).map(Customer::getEmail).orElse(null);

        PromoCode promo = promoCodeService.validate(
                code, totals.subtotal(), totals.discountedSubtotal(), owner.customerId(), email);

        cart.setPromoCodeId(promo.getId());
        cart.setPromoCode(promo.getCode());
        return price(cart, language);
    }

    @Transactional
    public CartDtos.CartResponse removePromoCode(CartOwner owner, String language) {
        Cart cart = requireCart(owner);
        cart.clearPromoCode();
        return price(cart, language);
    }

    // --- merge on login ---------------------------------------------------

    /**
     * Folds an anonymous cart into the customer's cart at login. Quantities add up and
     * the guest cart is marked CONVERTED so the unique index on the session stays free.
     */
    @Transactional
    public void mergeGuestCart(String sessionId, Long customerId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Optional<Cart> guestCart = cartRepository.findActiveBySession(sessionId);
        if (guestCart.isEmpty() || guestCart.get().isEmpty()) {
            guestCart.ifPresent(cart -> cart.setStatus(Cart.Status.CONVERTED));
            return;
        }

        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId));

        Cart guest = guestCart.get();
        Cart target = cartRepository.findActiveByCustomer(customerId).orElse(null);

        if (target == null) {
            // No customer cart yet: adopt the guest cart wholesale.
            guest.setCustomer(customer);
            guest.setSessionId(null);
            log.debug("Adopted guest cart {} for customer {}", guest.getId(), customerId);
            return;
        }

        for (CartItem guestItem : guest.getItems()) {
            Long variantId = guestItem.getVariant() == null ? null : guestItem.getVariant().getId();
            Optional<CartItem> existing = target.findItem(guestItem.getProduct().getId(), variantId);
            if (existing.isPresent()) {
                CartItem item = existing.get();
                item.setQuantity(clampQuantity(item.getProduct(), item.getVariant(),
                        item.getQuantity() + guestItem.getQuantity()));
            } else {
                target.getItems().add(CartItem.builder()
                        .cart(target)
                        .product(guestItem.getProduct())
                        .variant(guestItem.getVariant())
                        .quantity(guestItem.getQuantity())
                        .unitPrice(guestItem.getUnitPrice())
                        .originalUnitPrice(guestItem.getOriginalUnitPrice())
                        .discountId(guestItem.getDiscountId())
                        .build());
            }
        }
        if (target.getPromoCode() == null && guest.getPromoCode() != null) {
            target.setPromoCodeId(guest.getPromoCodeId());
            target.setPromoCode(guest.getPromoCode());
        }

        guest.getItems().clear();
        guest.setStatus(Cart.Status.CONVERTED);
        log.debug("Merged guest cart {} into customer cart {}", guest.getId(), target.getId());
    }

    // =======================================================================
    // Pricing
    // =======================================================================

    /**
     * Re-resolves every line against live campaigns, collects notices for anything that
     * changed, and computes the totals.
     */
    public CartDtos.CartResponse price(Cart cart, String language) {
        String fallback = languageService.defaultLanguageCode();
        var context = discountResolver.context();
        List<CartDtos.CartNotice> notices = new ArrayList<>();
        List<CartDtos.CartLine> lines = new ArrayList<>();
        boolean checkoutAllowed = !cart.isEmpty();

        for (CartItem item : List.copyOf(cart.getItems())) {
            Product product = item.getProduct();
            ProductVariant variant = item.getVariant();
            var translation = product.translation(language, fallback);
            String name = translation != null ? translation.getName() : product.getSku();

            // A product that was archived or hidden after being added must not silently
            // remain in the cart — nor a variant that was deactivated since.
            if (!product.isPublished() || !product.isPurchasable() || !product.isShowPrice()
                    || (variant != null && !variant.isActive())) {
                notices.add(new CartDtos.CartNotice("PRODUCT_UNAVAILABLE", name,
                        "Məhsul artıq satışda deyil və səbətdən çıxarıldı"));
                cart.getItems().remove(item);
                continue;
            }

            PriceInfo price = context.priceFor(product);
            boolean priceOverridden = variant != null && variant.getPriceOverride() != null;

            if (!priceOverridden && price.finalPrice() != null
                    && item.getUnitPrice().compareTo(price.finalPrice()) != 0) {
                notices.add(new CartDtos.CartNotice(
                        price.finalPrice().compareTo(item.getUnitPrice()) < 0 ? "PRICE_DROPPED" : "PRICE_INCREASED",
                        name, "Məhsulun qiyməti yeniləndi"));
                item.setUnitPrice(price.finalPrice());
                item.setOriginalUnitPrice(price.listPrice());
                item.setDiscountId(price.discountId());
            }

            boolean trackStock = variant != null ? variant.isTrackStock() : product.isTrackStock();
            boolean allowBackorder = variant != null ? variant.isAllowBackorder() : product.isAllowBackorder();
            int available = variant != null ? variant.availableQuantity() : product.availableQuantity();
            if (trackStock && !allowBackorder && item.getQuantity() > available) {
                if (available <= 0) {
                    notices.add(new CartDtos.CartNotice("OUT_OF_STOCK", name,
                            "Məhsul anbarda yoxdur"));
                    checkoutAllowed = false;
                } else {
                    notices.add(new CartDtos.CartNotice("QUANTITY_REDUCED", name,
                            "Anbarda yalnız %d ədəd var".formatted(available)));
                    item.setQuantity(available);
                }
            }

            String imageUrl = variant != null && variant.getImage() != null
                    ? variant.getImage().getPublicUrl()
                    : Optional.ofNullable(product.mainImage())
                            .map(image -> image.getMedia().getPublicUrl()).orElse(null);

            lines.add(new CartDtos.CartLine(
                    item.getId(), product.getId(), product.getSlug(), product.getSku(), name, imageUrl,
                    item.getQuantity(), item.getUnitPrice(), item.getOriginalUnitPrice(),
                    item.lineTotal(), item.lineSaving(), item.isDiscounted(),
                    priceOverridden ? null : price.discountName(),
                    priceOverridden ? null : price.badgeLabel(),
                    priceOverridden ? 0 : price.savingPercentage(),
                    variant != null ? variant.stockStatus(product.getLowStockThreshold()) : product.stockStatus(),
                    trackStock ? available : null,
                    product.getMinOrderQuantity(), product.getMaxOrderQuantity(),
                    variant != null ? variant.inStock() : product.inStock(),
                    variant != null ? variant.getId() : null,
                    variant != null ? variant.getSku() : null,
                    variant != null ? variant.optionLabel(language, fallback) : null));
        }

        // Promo code is re-validated on every read so an expired one drops off by itself.
        PromoCode promo = null;
        CartDtos.AppliedPromo appliedPromo = null;
        if (cart.getPromoCodeId() != null) {
            Totals draft = computeTotals(cart, null, null);
            try {
                String email = cart.getCustomer() == null ? null : cart.getCustomer().getEmail();
                promo = promoCodeService.validate(cart.getPromoCode(), draft.subtotal(),
                        draft.discountedSubtotal(),
                        cart.getCustomer() == null ? null : cart.getCustomer().getId(), email);
            } catch (ApiException ex) {
                notices.add(new CartDtos.CartNotice("PROMO_REMOVED", null,
                        "Promo kod artıq tətbiq edilmir: " + ex.getMessage()));
                cart.clearPromoCode();
            }
        }

        Totals totals = computeTotals(cart, promo, null);
        if (promo != null) {
            appliedPromo = new CartDtos.AppliedPromo(promo.getCode(), promo.getDescription(),
                    totals.promoDiscount(), promo.isFreeDelivery());
        }

        if (lines.isEmpty()) {
            checkoutAllowed = false;
        }

        SiteConfigSnapshot config = siteConfigurationService.publishedSnapshot();
        BigDecimal minOrder = config.minOrderTotal();
        if (minOrder.signum() > 0 && totals.subtotal().compareTo(minOrder) < 0) {
            notices.add(new CartDtos.CartNotice("MIN_ORDER_NOT_MET", null,
                    "Minimum sifariş məbləği %s %s-dir".formatted(
                            minOrder.stripTrailingZeros().toPlainString(), config.currency())));
            checkoutAllowed = false;
        }

        return new CartDtos.CartResponse(
                cart.getId(), cart.getSessionId(), cart.getCurrency(), lines,
                totals.toDto(lines.size(), cart.totalQuantity(), cart.getCurrency()),
                appliedPromo, notices, checkoutAllowed);
    }

    /**
     * Money maths for a cart. Kept free of entity mutation so checkout can call it to
     * verify the numbers it is about to freeze onto an order.
     */
    public Totals computeTotals(Cart cart, PromoCode promo, BigDecimal deliveryOverride) {
        BigDecimal originalSubtotal = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountedSubtotal = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            originalSubtotal = originalSubtotal.add(item.lineOriginalTotal());
            subtotal = subtotal.add(item.lineTotal());
            if (item.isDiscounted()) {
                discountedSubtotal = discountedSubtotal.add(item.lineTotal());
            }
        }

        BigDecimal itemDiscountTotal = originalSubtotal.subtract(subtotal);
        BigDecimal promoDiscount = promo == null
                ? BigDecimal.ZERO
                : promoCodeService.amountFor(promo, subtotal, discountedSubtotal);

        SiteConfigSnapshot config = siteConfigurationService.publishedSnapshot();
        BigDecimal threshold = config.freeDeliveryThreshold();
        BigDecimal basePrice = deliveryOverride != null ? deliveryOverride : config.defaultDeliveryPrice();

        BigDecimal afterPromo = subtotal.subtract(promoDiscount).max(BigDecimal.ZERO);
        boolean freeByThreshold = threshold.signum() > 0 && afterPromo.compareTo(threshold) >= 0;
        boolean freeByPromo = promo != null && promo.isFreeDelivery();
        boolean freeDelivery = cart.isEmpty() || freeByThreshold || freeByPromo;

        BigDecimal deliveryPrice = freeDelivery ? BigDecimal.ZERO : basePrice;
        BigDecimal total = afterPromo.add(deliveryPrice).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountToFreeDelivery = null;
        if (!freeDelivery && threshold.signum() > 0) {
            BigDecimal remaining = threshold.subtract(afterPromo);
            amountToFreeDelivery = remaining.signum() > 0 ? remaining.setScale(2, RoundingMode.HALF_UP) : null;
        }

        return new Totals(
                originalSubtotal.setScale(2, RoundingMode.HALF_UP),
                subtotal.setScale(2, RoundingMode.HALF_UP),
                discountedSubtotal.setScale(2, RoundingMode.HALF_UP),
                itemDiscountTotal.setScale(2, RoundingMode.HALF_UP),
                promoDiscount.setScale(2, RoundingMode.HALF_UP),
                deliveryPrice.setScale(2, RoundingMode.HALF_UP),
                total,
                freeDelivery,
                amountToFreeDelivery);
    }

    /** Result of {@link #computeTotals}; also the input to order creation. */
    public record Totals(
            BigDecimal originalSubtotal,
            BigDecimal subtotal,
            BigDecimal discountedSubtotal,
            BigDecimal itemDiscountTotal,
            BigDecimal promoDiscount,
            BigDecimal deliveryPrice,
            BigDecimal total,
            boolean freeDelivery,
            BigDecimal amountToFreeDelivery
    ) {
        CartDtos.CartTotals toDto(int itemCount, int totalQuantity, String currency) {
            return new CartDtos.CartTotals(
                    originalSubtotal, subtotal, itemDiscountTotal, promoDiscount,
                    itemDiscountTotal.add(promoDiscount), deliveryPrice, total, currency,
                    itemCount, totalQuantity, freeDelivery, amountToFreeDelivery);
        }
    }

    // =======================================================================
    // Internals
    // =======================================================================

    Optional<Cart> findCart(CartOwner owner) {
        if (owner.customerId() != null) {
            Optional<Cart> byCustomer = cartRepository.findActiveByCustomer(owner.customerId());
            if (byCustomer.isPresent()) {
                return byCustomer;
            }
        }
        if (owner.sessionId() != null && !owner.sessionId().isBlank()) {
            return cartRepository.findActiveBySession(owner.sessionId());
        }
        return Optional.empty();
    }

    public Cart requireCart(CartOwner owner) {
        return findCart(owner).orElseThrow(() -> new ApiException(ErrorCode.CART_NOT_FOUND));
    }

    private Cart createCart(CartOwner owner) {
        if (owner.customerId() == null && (owner.sessionId() == null || owner.sessionId().isBlank())) {
            throw ApiException.badRequest("Səbət üçün sessiya identifikatoru tələb olunur");
        }
        Cart cart = Cart.builder()
                .customer(owner.customerId() == null ? null
                        : customerRepository.findAliveById(owner.customerId()).orElse(null))
                .sessionId(owner.customerId() == null ? owner.sessionId() : null)
                .currency(currency())
                .status(Cart.Status.ACTIVE)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();
        return cartRepository.save(cart);
    }

    private int clampQuantity(Product product, ProductVariant variant, int requested) {
        int quantity = Math.max(requested, product.getMinOrderQuantity());
        if (product.getMaxOrderQuantity() != null) {
            quantity = Math.min(quantity, product.getMaxOrderQuantity());
        }
        boolean trackStock = variant != null ? variant.isTrackStock() : product.isTrackStock();
        boolean allowBackorder = variant != null ? variant.isAllowBackorder() : product.isAllowBackorder();
        int stockQuantity = variant != null ? variant.getStockQuantity() : product.getStockQuantity();
        if (trackStock && !allowBackorder) {
            quantity = Math.min(quantity, Math.max(stockQuantity, 0));
        }
        return Math.min(Math.max(quantity, 1), MAX_QUANTITY_PER_LINE);
    }

    private void requirePurchasable(Product product) {
        if (!product.isPublished() || !product.isPurchasable() || !product.isShowPrice()
                || product.getPrice() == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        if (!product.inStock()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private void requireCommerceEnabled() {
        if (!properties.getCommerce().isEnabled()
                || !siteConfigurationService.publishedSnapshot().cartEnabled()) {
            throw new ApiException(ErrorCode.COMMERCE_DISABLED);
        }
    }

    private String currency() {
        return siteConfigurationService.publishedSnapshot().currency();
    }

    /** Housekeeping: carts idle for 30 days become ABANDONED. Runs nightly at 03:40. */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void markAbandonedCarts() {
        int updated = cartRepository.markAbandoned(OffsetDateTime.now().minusDays(30));
        if (updated > 0) {
            log.info("Marked {} idle carts as abandoned", updated);
        }
    }

    /**
     * "Did you forget something?" — a cart still sitting there a few hours after the
     * last touch, for a customer we can actually reach by e-mail. Every 30 minutes is
     * frequent enough that the reminder still feels timely against a multi-hour
     * threshold, without hammering the query on every cart write.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void sendAbandonmentReminders() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.getCommerce().getCartReminderAfter());
        List<Cart> candidates = cartRepository.findNeedingAbandonmentReminder(cutoff);
        if (candidates.isEmpty()) {
            return;
        }

        String fallback = languageService.defaultLanguageCode();
        OffsetDateTime now = OffsetDateTime.now();

        for (Cart cart : candidates) {
            Customer customer = cart.getCustomer();
            if (customer == null || customer.getEmail() == null) {
                continue;
            }
            Product firstProduct = cart.getItems().get(0).getProduct();
            String firstItemName = firstProduct == null
                    ? "məhsul"
                    : Optional.ofNullable(firstProduct.translation(fallback, fallback))
                            .map(t -> t.getName())
                            .orElse(firstProduct.getSku());

            mailService.sendCartReminder(
                    customer.getEmail(), customer.getFirstName(),
                    firstItemName, cart.getItems().size(),
                    properties.getPublicUrl() + "/sebet");
            cart.setAbandonedReminderSentAt(now);
        }
        log.info("Sent {} cart-abandonment reminder(s)", candidates.size());
    }
}
