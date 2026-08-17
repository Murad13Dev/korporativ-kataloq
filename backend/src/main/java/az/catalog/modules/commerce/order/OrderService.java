package az.catalog.modules.commerce.order;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.security.SecurityUtils;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.catalog.product.ProductVariantRepository;
import az.catalog.modules.commerce.cart.CartDtos;
import az.catalog.modules.commerce.cart.CartOwner;
import az.catalog.modules.commerce.cart.CartService;
import az.catalog.modules.commerce.customer.ReferralService;
import az.catalog.modules.commerce.loyalty.LoyaltyService;
import az.catalog.modules.commerce.promo.PromoCode;
import az.catalog.modules.commerce.promo.PromoCodeRepository;
import az.catalog.modules.commerce.promo.PromoCodeService;
import az.catalog.modules.notification.MailService;
import az.catalog.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order lifecycle: status transitions, stock movements and the notifications that go with
 * them. Order <em>creation</em> lives in {@link CheckoutService}; everything after that
 * point is here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LoyaltyService loyaltyService;
    private final ReferralService referralService;
    private final CartService cartService;
    private final PromoCodeService promoCodeService;
    private final PromoCodeRepository promoCodeRepository;
    private final MailService mailService;
    private final AppProperties properties;

    // =======================================================================
    // Reads
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<OrderDtos.OrderSummary> adminSearch(String search, OrderStatus status,
                                                    PaymentStatus paymentStatus,
                                                    OffsetDateTime from, OffsetDateTime to,
                                                    Pageable pageable) {
        return orderRepository.search(blankToNull(search), status, paymentStatus, from, to, pageable)
                .map(OrderDtos.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderDtos.OrderSummary> customerOrders(Long customerId, Pageable pageable) {
        return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(OrderDtos.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderDtos.SavingsSummary savingsSummary(Long customerId) {
        OffsetDateTime yearStart = OffsetDateTime.now()
                .withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        BigDecimal lifetime = orderRepository.sumItemSavings(customerId, null)
                .add(orderRepository.sumOrderLevelSavings(customerId, null));
        BigDecimal thisYear = orderRepository.sumItemSavings(customerId, yearStart)
                .add(orderRepository.sumOrderLevelSavings(customerId, yearStart));

        return new OrderDtos.SavingsSummary(lifetime, thisYear);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail get(Long id) {
        return toDetail(orderRepository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Sifariş", id)));
    }

    /** Customer-scoped read: an order id from another account must 404, not 403. */
    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail getForCustomer(Long id, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> ApiException.notFound("Sifariş", id));
        return toDetail(order);
    }

    /**
     * "Reorder" — every line from a past order of this customer's, re-added to their
     * current cart at today's prices (a campaign that has since ended or started is not
     * replayed; the cart always prices against what's live right now, same as any other
     * add-to-cart). A line whose product was deleted since, or that's now out of stock,
     * is skipped and reported rather than blocking the rest of the order.
     */
    @Transactional
    public CartDtos.BulkAddResponse reorder(Long orderId, Long customerId, String language) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Sifariş", orderId));

        List<CartDtos.BulkLine> lines = order.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .map(item -> new CartDtos.BulkLine(
                        item.getProduct().getId(), item.getVariantId(), item.getQuantity(), item.getProductName()))
                .toList();

        CartDtos.BulkAddResponse result = cartService.bulkAdd(new CartOwner(customerId, null), lines, language);

        // A line whose product no longer exists at all never reached bulkAdd, so it needs
        // its own skip message — otherwise it would just silently vanish from the reorder.
        List<String> skipped = new ArrayList<>(result.skipped());
        order.getItems().stream()
                .filter(item -> item.getProduct() == null)
                .forEach(item -> skipped.add(item.getProductName() + ": məhsul artıq mövcud deyil"));

        return new CartDtos.BulkAddResponse(result.cart(), result.addedCount(), skipped);
    }

    /** Guest tracking: the order number alone is guessable, so the e-mail must match. */
    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail track(String orderNumber, String email) {
        Order order = orderRepository.findForGuest(orderNumber.trim(), email.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Bu nömrə və e-poçt ilə sifariş tapılmadı"));
        return toDetail(order);
    }

    @Transactional(readOnly = true)
    public Order require(Long id) {
        return orderRepository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Sifariş", id));
    }

    // =======================================================================
    // Status transitions
    // =======================================================================

    /**
     * Moves an order along its lifecycle, rejecting transitions the state machine does not
     * allow. Side effects (stock release, timestamps, e-mail) are derived from the target
     * status here rather than duplicated at each call site.
     */
    @Transactional
    public OrderDtos.OrderDetail changeStatus(Long orderId, OrderStatus target, String note,
                                              OrderStatusHistory.ActorType actorType) {
        Order order = require(orderId);
        OrderStatus current = order.getStatus();

        if (current == target) {
            return toDetail(order);
        }
        if (!current.canTransitionTo(target)) {
            throw new ApiException(ErrorCode.ORDER_STATUS_TRANSITION_INVALID,
                    "'%s' statusundan '%s' statusuna keçid mümkün deyil"
                            .formatted(current.label(), target.label()));
        }

        applyTransition(order, target, note, actorType);
        return toDetail(order);
    }

    /** Shared by the admin action, the payment callback and the scheduled expiry job. */
    @Transactional
    public void applyTransition(Order order, OrderStatus target, String note,
                                OrderStatusHistory.ActorType actorType) {
        OrderStatus from = order.getStatus();
        order.setStatus(target);

        OffsetDateTime now = OffsetDateTime.now();
        switch (target) {
            case CONFIRMED -> {
                if (order.getPlacedAt() == null) {
                    order.setPlacedAt(now);
                }
            }
            case SHIPPED -> order.setShippedAt(now);
            case DELIVERED -> order.setDeliveredAt(now);
            case CANCELLED, EXPIRED -> {
                order.setCancelledAt(now);
                if (note != null) {
                    order.setCancelReason(note);
                }
                releaseStock(order);
                promoCodeService.release(promoCodeIdOf(order), order.getId());
                if (order.getPaymentStatus() == PaymentStatus.PENDING) {
                    order.setPaymentStatus(PaymentStatus.CANCELLED);
                }
            }
            case REFUNDED -> {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
                releaseStock(order);
            }
            case COMPLETED -> {
                loyaltyService.earnForOrder(order);
                referralService.rewardIfFirstOrder(order);
            }
            default -> {
                // PENDING / AWAITING_PAYMENT / PROCESSING carry no extra effect.
            }
        }

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(from)
                .toStatus(target)
                .note(note)
                .changedBy(SecurityUtils.currentPrincipal().map(p -> p.id()).orElse(null))
                .actorType(actorType)
                .build());

        log.info("Order {} moved {} → {} by {}", order.getOrderNumber(), from, target, actorType);

        // The customer is told about states they can act on, not about internal bookkeeping.
        if (target == OrderStatus.SHIPPED || target == OrderStatus.DELIVERED
                || target == OrderStatus.CANCELLED || target == OrderStatus.REFUNDED) {
            mailService.sendOrderStatusChanged(order.getCustomerEmail(), order.getCustomerName(),
                    order.getOrderNumber(), target.label(), orderLink(order));
        }
    }

    /** Marks an order paid. Called by {@code PaymentService} once a payment settles. */
    @Transactional
    public void markPaid(Order order, OrderStatusHistory.ActorType actorType) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.debug("Order {} already paid — ignoring duplicate settlement", order.getOrderNumber());
            return;
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(OffsetDateTime.now());
        order.setPaymentExpiresAt(null);

        if (order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)) {
            applyTransition(order, OrderStatus.CONFIRMED, "Ödəniş qəbul edildi", actorType);
        }

        mailService.sendPaymentReceipt(order.getCustomerEmail(), order.getCustomerName(),
                order.getOrderNumber(), order.getTotal(), order.getCurrency());
    }

    @Transactional
    public void markPaymentFailed(Order order, String reason) {
        order.setPaymentStatus(PaymentStatus.FAILED);
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(order.getStatus())
                .toStatus(order.getStatus())
                .note("Ödəniş uğursuz oldu: " + reason)
                .actorType(OrderStatusHistory.ActorType.PAYMENT_PROVIDER)
                .build());
        // The order stays AWAITING_PAYMENT so the shopper can retry until the window closes.
    }

    @Transactional
    public OrderDtos.OrderDetail cancelByCustomer(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Sifariş", orderId));

        if (!order.isCancellableByCustomer()) {
            throw new ApiException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        applyTransition(order, OrderStatus.CANCELLED,
                reason == null || reason.isBlank() ? "Müştəri tərəfindən ləğv edildi" : reason,
                OrderStatusHistory.ActorType.CUSTOMER);
        return toDetail(order);
    }

    @Transactional
    public OrderDtos.OrderDetail setAdminNote(Long orderId, String note) {
        Order order = require(orderId);
        order.setAdminNote(note);
        return toDetail(order);
    }

    // =======================================================================
    // Stock
    // =======================================================================

    /**
     * Reserves stock for every tracked line. The repository guard makes each decrement a
     * compare-and-set, so two checkouts racing for the last unit cannot both win.
     */
    @Transactional
    public void reserveStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariantId() != null) {
                int updated = variantRepository.decrementStock(item.getVariantId(), item.getQuantity());
                if (updated == 0) {
                    throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                            "'%s' üçün anbarda kifayət qədər məhsul yoxdur".formatted(item.getProductName()));
                }
                continue;
            }
            if (item.getProduct() == null || !item.getProduct().isTrackStock()) {
                continue;
            }
            int updated = productRepository.decrementStock(item.getProduct().getId(), item.getQuantity());
            if (updated == 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                        "'%s' üçün anbarda kifayət qədər məhsul yoxdur".formatted(item.getProductName()));
            }
        }
    }

    private void releaseStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariantId() != null) {
                variantRepository.incrementStock(item.getVariantId(), item.getQuantity());
            } else if (item.getProduct() != null && item.getProduct().isTrackStock()) {
                productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
            }
        }
    }

    // =======================================================================
    // Scheduled housekeeping
    // =======================================================================

    /**
     * Expires orders whose payment window elapsed, returning their stock and vouchers.
     * Runs every five minutes; the window itself is {@code app.commerce.payment-window-minutes}.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void expireUnpaidOrders() {
        List<Order> expired = orderRepository.findExpiredAwaitingPayment(OffsetDateTime.now());
        for (Order order : expired) {
            try {
                applyTransition(order, OrderStatus.EXPIRED,
                        "Ödəniş üçün ayrılan vaxt bitdi", OrderStatusHistory.ActorType.SYSTEM);
            } catch (RuntimeException ex) {
                log.error("Failed to expire order {}", order.getOrderNumber(), ex);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} unpaid orders", expired.size());
        }
    }

    // =======================================================================
    // Mapping
    // =======================================================================

    OrderDtos.OrderDetail toDetail(Order order) {
        return new OrderDtos.OrderDetail(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getStatus().label(),
                order.getPaymentStatus(), order.getPaymentMethod(), order.getDeliveryMethod(),
                order.getCustomer() == null ? null : order.getCustomer().getId(),
                order.getCustomerName(), order.getCustomerEmail(), order.getCustomerPhone(),
                order.getCompanyName(),
                order.getShippingCountry(), order.getShippingCity(), order.getShippingDistrict(),
                order.getShippingAddressLine(), order.getShippingPostalCode(),
                order.getSubtotal(), order.getDiscountTotal(), order.getPromoDiscount(),
                order.getPromoCode(), order.getCorporateDiscountAmount(),
                order.getDeliveryPrice(), order.getTotal(),
                order.totalSaving(), order.getCurrency(),
                order.getCustomerNote(), order.getAdminNote(),
                order.getItems().stream().map(OrderDtos.OrderItemDto::from).toList(),
                historyRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId())
                        .stream().map(OrderDtos.StatusHistoryDto::from).toList(),
                List.of(),
                order.getCreatedAt(), order.getPlacedAt(), order.getPaidAt(), order.getShippedAt(),
                order.getDeliveredAt(), order.getCancelledAt(), order.getCancelReason(),
                order.getPaymentExpiresAt(), order.isCancellableByCustomer());
    }

    private String orderLink(Order order) {
        return properties.getPublicUrl() + "/hesab/sifarisler/" + order.getId();
    }

    /**
     * The order snapshots the promo <em>code</em> rather than its id, so releasing a
     * voucher resolves the id back from the code. A code deleted in the meantime simply
     * has nothing to release.
     */
    private Long promoCodeIdOf(Order order) {
        if (order.getPromoCode() == null) {
            return null;
        }
        return promoCodeRepository.findAliveByCode(order.getPromoCode())
                .map(PromoCode::getId)
                .orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
