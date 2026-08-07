package az.catalog.modules.commerce.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    // -----------------------------------------------------------------------
    // Checkout
    // -----------------------------------------------------------------------

    public record CheckoutRequest(
            // --- contact ---
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 40) String phone,
            @Size(max = 160) String companyName,

            // --- delivery ---
            @NotNull DeliveryMethod deliveryMethod,
            /** Existing saved address; when set the fields below are ignored. */
            Long addressId,
            @Size(max = 80) String country,
            @Size(max = 120) String city,
            @Size(max = 120) String district,
            @Size(max = 1000) String addressLine,
            @Size(max = 24) String postalCode,

            // --- payment ---
            @NotNull PaymentMethod paymentMethod,

            @Size(max = 1000) String customerNote,
            /** Save the typed address to the account (ignored for guests). */
            Boolean saveAddress,
            Boolean marketingOptIn,

            @AssertTrue(message = "Şərtlər qəbul edilməlidir")
            boolean termsAccepted
    ) {
    }

    /**
     * @param paymentUrl where to redirect for online payment; null for cash on delivery
     */
    public record CheckoutResponse(
            Long orderId,
            String orderNumber,
            OrderStatus status,
            PaymentStatus paymentStatus,
            BigDecimal total,
            String currency,
            String paymentUrl,
            String paymentReference,
            OffsetDateTime paymentExpiresAt
    ) {
    }

    // -----------------------------------------------------------------------
    // Order views
    // -----------------------------------------------------------------------

    public record OrderSummary(
            Long id,
            String orderNumber,
            OrderStatus status,
            String statusLabel,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String customerName,
            String customerEmail,
            BigDecimal total,
            String currency,
            int itemCount,
            OffsetDateTime createdAt,
            OffsetDateTime placedAt
    ) {
        public static OrderSummary from(Order order) {
            return new OrderSummary(
                    order.getId(), order.getOrderNumber(), order.getStatus(), order.getStatus().label(),
                    order.getPaymentStatus(), order.getPaymentMethod(), order.getCustomerName(),
                    order.getCustomerEmail(), order.getTotal(), order.getCurrency(),
                    order.getItems().size(), order.getCreatedAt(), order.getPlacedAt());
        }
    }

    public record OrderDetail(
            Long id,
            String orderNumber,
            OrderStatus status,
            String statusLabel,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            DeliveryMethod deliveryMethod,

            Long customerId,
            String customerName,
            String customerEmail,
            String customerPhone,
            String companyName,

            String shippingCountry,
            String shippingCity,
            String shippingDistrict,
            String shippingAddressLine,
            String shippingPostalCode,

            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal promoDiscount,
            String promoCode,
            BigDecimal corporateDiscountAmount,
            BigDecimal deliveryPrice,
            BigDecimal total,
            BigDecimal totalSaving,
            String currency,

            String customerNote,
            String adminNote,

            List<OrderItemDto> items,
            List<StatusHistoryDto> history,
            List<PaymentDto> payments,

            OffsetDateTime createdAt,
            OffsetDateTime placedAt,
            OffsetDateTime paidAt,
            OffsetDateTime shippedAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime cancelledAt,
            String cancelReason,
            OffsetDateTime paymentExpiresAt,
            boolean cancellable
    ) {
    }

    public record OrderItemDto(
            Long id,
            Long productId,
            String productName,
            String productSku,
            String productSlug,
            String imageUrl,
            int quantity,
            BigDecimal originalUnitPrice,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            String discountName,
            BigDecimal totalPrice,
            boolean discounted,
            String variantSku,
            String variantLabel
    ) {
        public static OrderItemDto from(OrderItem item) {
            return new OrderItemDto(
                    item.getId(),
                    item.getProduct() == null ? null : item.getProduct().getId(),
                    item.getProductName(), item.getProductSku(), item.getProductSlug(),
                    item.getImageUrl(), item.getQuantity(), item.getOriginalUnitPrice(),
                    item.getUnitPrice(), item.getDiscountAmount(), item.getDiscountName(),
                    item.getTotalPrice(), item.wasDiscounted(),
                    item.getVariantSku(), item.getVariantLabel());
        }
    }

    public record StatusHistoryDto(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String toStatusLabel,
            String note,
            OrderStatusHistory.ActorType actorType,
            OffsetDateTime createdAt
    ) {
        public static StatusHistoryDto from(OrderStatusHistory history) {
            return new StatusHistoryDto(
                    history.getFromStatus(), history.getToStatus(), history.getToStatus().label(),
                    history.getNote(), history.getActorType(), history.getCreatedAt());
        }
    }

    public record PaymentDto(
            Long id,
            String provider,
            String reference,
            String externalTransactionId,
            BigDecimal amount,
            BigDecimal refundedAmount,
            String status,
            String cardMask,
            String failureMessage,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }

    // -----------------------------------------------------------------------
    // Admin actions
    // -----------------------------------------------------------------------

    public record StatusChangeRequest(
            @NotNull OrderStatus status,
            @Size(max = 500) String note
    ) {
    }

    public record CancelRequest(@Size(max = 500) String reason) {
    }

    public record AdminNoteRequest(@Size(max = 4000) String note) {
    }

    public record RefundRequest(
            BigDecimal amount,
            @Size(max = 500) String reason
    ) {
    }

    /** Guest order tracking: number + e-mail, no account required. */
    public record TrackRequest(
            @NotBlank @Size(max = 32) String orderNumber,
            @NotBlank @Email @Size(max = 190) String email
    ) {
    }
}
