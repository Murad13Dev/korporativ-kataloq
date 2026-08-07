package az.catalog.modules.commerce.order;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order.
 *
 * Everything an order needs is snapshotted onto it: the customer's name, the shipping
 * address, and every line's product name, SKU and price. A product being renamed,
 * repriced or deleted afterwards must never change what a customer was charged, and an
 * order must stay readable even if its products are gone.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Column(name = "order_number", nullable = false, length = 32)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // --- contact snapshot -------------------------------------------------

    @Column(name = "customer_name", nullable = false, length = 160)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 190)
    private String customerEmail;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    @Column(name = "company_name", length = 160)
    private String companyName;

    // --- shipping snapshot ------------------------------------------------

    @Column(name = "shipping_country", length = 80)
    private String shippingCountry;

    @Column(name = "shipping_city", length = 120)
    private String shippingCity;

    @Column(name = "shipping_district", length = 120)
    private String shippingDistrict;

    @Column(name = "shipping_address_line", columnDefinition = "text")
    private String shippingAddressLine;

    @Column(name = "shipping_postal_code", length = 24)
    private String shippingPostalCode;

    @Column(name = "shipping_notes", length = 500)
    private String shippingNotes;

    // --- state ------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 24)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.ONLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 32)
    @Builder.Default
    private DeliveryMethod deliveryMethod = DeliveryMethod.COURIER;

    // --- money ------------------------------------------------------------

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Sum of per-line campaign savings. */
    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountTotal = BigDecimal.ZERO;

    /** Cart-level voucher saving, kept apart so both sides can see where the money went. */
    @Column(name = "promo_discount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal promoDiscount = BigDecimal.ZERO;

    @Column(name = "promo_code", length = 48)
    private String promoCode;

    @Column(name = "delivery_price", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal deliveryPrice = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "AZN";

    // --- misc -------------------------------------------------------------

    @Column(name = "customer_note", length = 1000)
    private String customerNote;

    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    @Column(name = "locale", nullable = false, length = 8)
    @Builder.Default
    private String locale = "az";

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "placed_at")
    private OffsetDateTime placedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** Online payments must complete before this instant or the order auto-expires. */
    @Column(name = "payment_expires_at")
    private OffsetDateTime paymentExpiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // --- behaviour --------------------------------------------------------

    public BigDecimal totalSaving() {
        return discountTotal.add(promoDiscount);
    }

    public int totalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }

    /** A customer may cancel while nothing has shipped and no money has moved. */
    public boolean isCancellableByCustomer() {
        return (status == OrderStatus.PENDING
                || status == OrderStatus.AWAITING_PAYMENT
                || status == OrderStatus.CONFIRMED)
                && paymentStatus != PaymentStatus.PAID;
    }

    public boolean isTerminal() {
        return status == OrderStatus.COMPLETED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.REFUNDED
                || status == OrderStatus.EXPIRED;
    }

    public boolean paymentWindowExpired() {
        return paymentExpiresAt != null && OffsetDateTime.now().isAfter(paymentExpiresAt);
    }

    public String shippingSummary() {
        StringBuilder builder = new StringBuilder();
        if (shippingCity != null) {
            builder.append(shippingCity);
        }
        if (shippingDistrict != null) {
            builder.append(", ").append(shippingDistrict);
        }
        if (shippingAddressLine != null) {
            builder.append(", ").append(shippingAddressLine);
        }
        return builder.toString();
    }
}
