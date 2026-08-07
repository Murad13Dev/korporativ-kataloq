package az.catalog.modules.commerce.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle with an explicit transition table.
 *
 * Statuses are not free-form: an order cannot jump from PENDING to DELIVERED, and a
 * cancelled order cannot come back. Encoding this here rather than in the controller
 * means the admin UI, the payment callbacks and the scheduled expiry job all obey the
 * same rules.
 */
public enum OrderStatus {

    /** Created, payment method not yet resolved. */
    PENDING,
    /** Online payment initiated, waiting for the provider. */
    AWAITING_PAYMENT,
    /** Paid, or cash-on-delivery accepted. Ready to be picked. */
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    /** Payment window elapsed without a successful payment. */
    EXPIRED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(AWAITING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED);
            case AWAITING_PAYMENT -> EnumSet.of(CONFIRMED, CANCELLED, EXPIRED);
            case CONFIRMED -> EnumSet.of(PROCESSING, SHIPPED, CANCELLED, REFUNDED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED, REFUNDED);
            case SHIPPED -> EnumSet.of(DELIVERED, REFUNDED);
            case DELIVERED -> EnumSet.of(COMPLETED, REFUNDED);
            case COMPLETED -> EnumSet.of(REFUNDED);
            case CANCELLED, REFUNDED, EXPIRED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }

    /** Human label used in status e-mails and the customer's order history. */
    public String label() {
        return switch (this) {
            case PENDING -> "Gözləyir";
            case AWAITING_PAYMENT -> "Ödəniş gözlənilir";
            case CONFIRMED -> "Təsdiqləndi";
            case PROCESSING -> "Hazırlanır";
            case SHIPPED -> "Yolda";
            case DELIVERED -> "Çatdırıldı";
            case COMPLETED -> "Tamamlandı";
            case CANCELLED -> "Ləğv edildi";
            case REFUNDED -> "Geri qaytarıldı";
            case EXPIRED -> "Vaxtı bitdi";
        };
    }

    /** Statuses after which stock must be released back. */
    public boolean releasesStock() {
        return this == CANCELLED || this == EXPIRED || this == REFUNDED;
    }
}
