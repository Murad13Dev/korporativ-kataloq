package az.catalog.modules.commerce.order;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.RequestUtils;
import az.catalog.modules.commerce.cart.Cart;
import az.catalog.modules.commerce.cart.CartItem;
import az.catalog.modules.commerce.cart.CartOwner;
import az.catalog.modules.commerce.cart.CartService;
import az.catalog.modules.commerce.company.CompanyMember;
import az.catalog.modules.commerce.company.CompanyMemberRepository;
import az.catalog.modules.commerce.company.CompanyStatus;
import az.catalog.modules.commerce.customer.Address;
import az.catalog.modules.commerce.customer.AddressRepository;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.commerce.payment.PaymentService;
import az.catalog.modules.commerce.promo.PromoCode;
import az.catalog.modules.commerce.promo.PromoCodeRepository;
import az.catalog.modules.commerce.promo.PromoCodeService;
import az.catalog.modules.notification.MailService;
import az.catalog.modules.siteconfig.SiteConfigSnapshot;
import az.catalog.modules.siteconfig.SiteConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Turns a cart into an order.
 *
 * <p>The whole thing runs in one transaction: validate, snapshot, reserve stock, redeem
 * the voucher, create the order. If any step fails — most often the stock compare-and-set
 * losing a race — nothing is persisted, no voucher is burned and no stock is held.
 *
 * <p>Prices are recomputed here from live campaigns rather than trusted from the cart, so
 * a stale browser tab cannot check out at yesterday's sale price.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderService orderService;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final PromoCodeService promoCodeService;
    private final PromoCodeRepository promoCodeRepository;
    private final PaymentService paymentService;
    private final MailService mailService;
    private final SiteConfigurationService siteConfigurationService;
    private final AppProperties properties;

    @Transactional
    public OrderDtos.CheckoutResponse checkout(CartOwner owner, OrderDtos.CheckoutRequest request,
                                               String language, HttpServletRequest httpRequest) {
        SiteConfigSnapshot config = siteConfigurationService.publishedSnapshot();
        requireCommerceEnabled(config);
        validatePaymentMethod(request.paymentMethod(), config);

        Cart cart = cartService.requireCart(owner);
        if (cart.isEmpty()) {
            throw new ApiException(ErrorCode.CART_EMPTY);
        }

        // Re-price against live campaigns and surface anything that changed. A blocking
        // notice (out of stock, below minimum) stops the checkout here rather than after
        // the shopper has been sent to the bank.
        var priced = cartService.price(cart, language);
        if (!priced.checkoutAllowed()) {
            String reason = priced.notices().isEmpty()
                    ? "Səbət sifariş üçün hazır deyil"
                    : priced.notices().get(0).message();
            throw new ApiException(ErrorCode.PRICE_CHANGED, reason);
        }

        Customer customer = resolveCustomer(owner, request, config);
        PromoCode promo = resolvePromo(cart, customer, request);
        CartService.Totals totals = cartService.computeTotals(cart, promo,
                request.deliveryMethod() == DeliveryMethod.PICKUP ? BigDecimal.ZERO : null);

        Order order = buildOrder(cart, customer, request, totals, promo, language, httpRequest, config);
        snapshotItems(order, cart);

        Order saved = orderRepository.save(order);

        // Stock first: if it fails, the transaction rolls back before the voucher is spent.
        orderService.reserveStock(saved);

        if (promo != null && totals.promoDiscount().signum() > 0) {
            promoCodeService.redeem(promo, saved.getId(),
                    customer == null ? null : customer.getId(),
                    saved.getCustomerEmail(), totals.promoDiscount());
        }

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(saved.getId())
                .toStatus(saved.getStatus())
                .note("Sifariş yaradıldı")
                .actorType(owner.isAnonymous()
                        ? OrderStatusHistory.ActorType.SYSTEM : OrderStatusHistory.ActorType.CUSTOMER)
                .build());

        // The cart is consumed regardless of whether payment later succeeds; a failed
        // payment is retried against the order, not the cart.
        cart.getItems().clear();
        cart.clearPromoCode();
        cart.setStatus(Cart.Status.CONVERTED);

        if (Boolean.TRUE.equals(request.saveAddress()) && customer != null
                && request.addressId() == null && request.deliveryMethod().requiresAddress()) {
            saveAddress(customer, request);
        }

        mailService.sendOrderConfirmation(saved.getCustomerEmail(), saved.getCustomerName(),
                saved.getOrderNumber(), saved.getTotal(), saved.getCurrency(),
                properties.getPublicUrl() + "/hesab/sifarisler/" + saved.getId());
        mailService.sendAdminOrderNotification(saved.getOrderNumber(), saved.getCustomerName(),
                saved.getTotal(), saved.getCurrency(), saved.getId());

        log.info("Created order {} ({} {}) via {}", saved.getOrderNumber(), saved.getTotal(),
                saved.getCurrency(), saved.getPaymentMethod());

        return finalise(saved, language);
    }

    // -----------------------------------------------------------------------

    /**
     * Online orders get a payment session and a deadline; everything else is confirmed
     * immediately.
     */
    private OrderDtos.CheckoutResponse finalise(Order order, String language) {
        if (!order.getPaymentMethod().requiresOnlinePayment()) {
            orderService.applyTransition(order, OrderStatus.CONFIRMED,
                    "Ödəniş üsulu: " + order.getPaymentMethod().label(),
                    OrderStatusHistory.ActorType.SYSTEM);

            return new OrderDtos.CheckoutResponse(
                    order.getId(), order.getOrderNumber(), order.getStatus(), order.getPaymentStatus(),
                    order.getTotal(), order.getCurrency(), null, null, null);
        }

        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setPaymentExpiresAt(OffsetDateTime.now()
                .plusMinutes(properties.getCommerce().getPaymentWindowMinutes()));

        var session = paymentService.initiate(order, language);

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(OrderStatus.PENDING)
                .toStatus(OrderStatus.AWAITING_PAYMENT)
                .note("Ödəniş başladıldı")
                .actorType(OrderStatusHistory.ActorType.SYSTEM)
                .build());

        return new OrderDtos.CheckoutResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getPaymentStatus(),
                order.getTotal(), order.getCurrency(),
                session.paymentUrl(), session.reference(), order.getPaymentExpiresAt());
    }

    private Order buildOrder(Cart cart, Customer customer, OrderDtos.CheckoutRequest request,
                             CartService.Totals totals, PromoCode promo, String language,
                             HttpServletRequest httpRequest, SiteConfigSnapshot config) {

        BigDecimal corporateDiscount = corporateDiscountFor(customer, totals.subtotal());

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .customerName(request.fullName().trim())
                .customerEmail(request.email().trim().toLowerCase())
                .customerPhone(request.phone().trim())
                .companyName(request.companyName())
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .deliveryMethod(request.deliveryMethod())
                .subtotal(totals.subtotal())
                .discountTotal(totals.itemDiscountTotal())
                .promoDiscount(totals.promoDiscount())
                .promoCode(promo == null ? null : promo.getCode())
                .corporateDiscountAmount(corporateDiscount)
                .deliveryPrice(totals.deliveryPrice())
                .total(totals.total().subtract(corporateDiscount))
                .currency(config.currency())
                .customerNote(request.customerNote())
                .locale(language)
                .ipAddress(RequestUtils.clientIp(httpRequest))
                .build();

        if (request.deliveryMethod().requiresAddress()) {
            applyShippingAddress(order, customer, request);
        }
        return order;
    }

    /**
     * A registered customer whose company has been approved (status ACTIVE, not just
     * requested) gets their negotiated rate off the subtotal — stacked on top of, not
     * instead of, any campaign or promo-code saving already folded into {@code totals}.
     */
    private BigDecimal corporateDiscountFor(Customer customer, BigDecimal subtotal) {
        if (customer == null) {
            return BigDecimal.ZERO;
        }
        return companyMemberRepository.findByCustomerId(customer.getId())
                .map(CompanyMember::getCompany)
                .filter(company -> company.getStatus() == CompanyStatus.ACTIVE)
                .filter(company -> company.getDiscountPercentage().signum() > 0)
                .map(company -> percentOf(subtotal, company.getDiscountPercentage()))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void applyShippingAddress(Order order, Customer customer, OrderDtos.CheckoutRequest request) {
        if (request.addressId() != null) {
            if (customer == null) {
                throw ApiException.badRequest("Saxlanmış ünvan yalnız qeydiyyatlı istifadəçilər üçündür");
            }
            Address address = addressRepository.findByIdAndCustomerId(request.addressId(), customer.getId())
                    .orElseThrow(() -> ApiException.notFound("Ünvan", request.addressId()));

            order.setShippingCountry(address.getCountry());
            order.setShippingCity(address.getCity());
            order.setShippingDistrict(address.getDistrict());
            order.setShippingAddressLine(address.getAddressLine());
            order.setShippingPostalCode(address.getPostalCode());
            order.setShippingNotes(address.getNotes());
            return;
        }

        if (isBlank(request.city()) || isBlank(request.addressLine())) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "addressLine",
                    "Çatdırılma ünvanı tam doldurulmalıdır");
        }
        order.setShippingCountry(isBlank(request.country()) ? "Azərbaycan" : request.country());
        order.setShippingCity(request.city().trim());
        order.setShippingDistrict(request.district());
        order.setShippingAddressLine(request.addressLine().trim());
        order.setShippingPostalCode(request.postalCode());
    }

    /** Freezes each cart line onto the order so later catalog edits cannot rewrite history. */
    private void snapshotItems(Order order, Cart cart) {
        for (CartItem item : cart.getItems()) {
            var product = item.getProduct();
            var variant = item.getVariant();
            var translation = product.translation(order.getLocale(), order.getLocale());

            String imageUrl = variant != null && variant.getImage() != null
                    ? variant.getImage().getPublicUrl()
                    : Optional.ofNullable(product.mainImage())
                            .map(image -> image.getMedia().getPublicUrl()).orElse(null);

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(translation != null ? translation.getName() : product.getSku())
                    .productSku(product.getSku())
                    .productSlug(product.getSlug())
                    .variantId(variant == null ? null : variant.getId())
                    .variantSku(variant == null ? null : variant.getSku())
                    .variantLabel(variant == null ? null
                            : variant.optionLabel(order.getLocale(), order.getLocale()))
                    .imageUrl(imageUrl)
                    .quantity(item.getQuantity())
                    .originalUnitPrice(item.getOriginalUnitPrice())
                    .unitPrice(item.getUnitPrice())
                    .discountAmount(item.lineSaving())
                    .discountName(item.getDiscountId() == null ? null : "Kampaniya")
                    .taxRate(product.getTaxRate())
                    .totalPrice(item.lineTotal())
                    .build());
        }
    }

    /**
     * Finds or creates the customer record. Guest checkout still produces a row — an order
     * always needs someone to belong to, and the shopper can claim it later by registering
     * with the same address.
     */
    private Customer resolveCustomer(CartOwner owner, OrderDtos.CheckoutRequest request,
                                     SiteConfigSnapshot config) {
        if (owner.customerId() != null) {
            return customerRepository.findAliveById(owner.customerId())
                    .orElseThrow(() -> ApiException.notFound("Müştəri", owner.customerId()));
        }
        if (!config.guestCheckoutEnabled() || !properties.getCommerce().isGuestCheckoutEnabled()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "Sifariş vermək üçün hesabınıza daxil olun");
        }

        String email = request.email().trim().toLowerCase();
        return customerRepository.findAliveByEmail(email).orElseGet(() -> {
            String[] names = splitName(request.fullName());
            return customerRepository.save(Customer.builder()
                    .firstName(names[0])
                    .lastName(names[1])
                    .email(email)
                    .phone(request.phone())
                    .companyName(request.companyName())
                    .status(Customer.Status.GUEST)
                    .marketingOptIn(Boolean.TRUE.equals(request.marketingOptIn()))
                    .build());
        });
    }

    private PromoCode resolvePromo(Cart cart, Customer customer, OrderDtos.CheckoutRequest request) {
        if (cart.getPromoCodeId() == null) {
            return null;
        }
        var totals = cartService.computeTotals(cart, null, null);
        try {
            return promoCodeService.validate(cart.getPromoCode(), totals.subtotal(),
                    totals.discountedSubtotal(),
                    customer == null ? null : customer.getId(),
                    request.email());
        } catch (ApiException ex) {
            // A voucher that expired between the cart page and checkout must not block the
            // order — it is simply dropped and the shopper pays the undiscounted total.
            log.info("Dropping promo code {} at checkout: {}", cart.getPromoCode(), ex.getMessage());
            return null;
        }
    }

    private void saveAddress(Customer customer, OrderDtos.CheckoutRequest request) {
        boolean isFirst = addressRepository.countByCustomerId(customer.getId()) == 0;
        if (isFirst) {
            addressRepository.clearDefault(customer.getId());
        }
        addressRepository.save(Address.builder()
                .customer(customer)
                .title("Çatdırılma ünvanı")
                .fullName(request.fullName())
                .phone(request.phone())
                .country(isBlank(request.country()) ? "Azərbaycan" : request.country())
                .city(request.city())
                .district(request.district())
                .addressLine(request.addressLine())
                .postalCode(request.postalCode())
                .isDefault(isFirst)
                .build());
    }

    /**
     * ORD-20260805-000123. Readable, sortable and unique via a database sequence rather
     * than a count, so concurrent checkouts cannot collide.
     */
    private String generateOrderNumber() {
        Long sequence = orderRepository.nextOrderSequence();
        return "ORD-%s-%06d".formatted(OffsetDateTime.now().format(ORDER_DATE), sequence);
    }

    private void validatePaymentMethod(PaymentMethod method, SiteConfigSnapshot config) {
        if (method == PaymentMethod.ONLINE && !config.onlinePaymentEnabled()) {
            throw new ApiException(ErrorCode.COMMERCE_DISABLED, "Onlayn ödəniş aktiv deyil");
        }
    }

    private void requireCommerceEnabled(SiteConfigSnapshot config) {
        if (!properties.getCommerce().isEnabled() || !config.commerceEnabled()) {
            throw new ApiException(ErrorCode.COMMERCE_DISABLED);
        }
    }

    private String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.lastIndexOf(' ');
        return space > 0
                ? new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)}
                : new String[]{trimmed, "-"};
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
