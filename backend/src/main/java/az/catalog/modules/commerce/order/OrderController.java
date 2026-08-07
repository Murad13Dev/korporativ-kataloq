package az.catalog.modules.commerce.order;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import az.catalog.modules.commerce.payment.Payment;
import az.catalog.modules.commerce.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "Admin · Sifarişlər")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @Operation(summary = "Sifariş siyahısı")
    @GetMapping
    public PageResponse<OrderDtos.OrderSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(orderService.adminSearch(search, status, paymentStatus, from, to, pageable));
    }

    @Operation(summary = "Sifariş detalı")
    @GetMapping("/{id}")
    public DataResponse<OrderDtos.OrderDetail> get(@PathVariable Long id) {
        return DataResponse.of(orderService.get(id));
    }

    @Operation(summary = "Status dəyişmək",
            description = "Yalnız icazə verilən keçidlər qəbul olunur (bax: OrderStatus vəziyyət maşını).")
    @PatchMapping("/{id}/status")
    public DataResponse<OrderDtos.OrderDetail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody OrderDtos.StatusChangeRequest request) {
        return DataResponse.of(orderService.changeStatus(id, request.status(), request.note(),
                OrderStatusHistory.ActorType.ADMIN));
    }

    @Operation(summary = "Sifarişi ləğv etmək", description = "Anbar və promo kod geri qaytarılır.")
    @PostMapping("/{id}/cancel")
    public DataResponse<OrderDtos.OrderDetail> cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) OrderDtos.CancelRequest request) {
        String reason = request == null ? null : request.reason();
        return DataResponse.of(orderService.changeStatus(id, OrderStatus.CANCELLED, reason,
                OrderStatusHistory.ActorType.ADMIN));
    }

    @Operation(summary = "Daxili qeyd yazmaq")
    @PutMapping("/{id}/note")
    public DataResponse<OrderDtos.OrderDetail> setNote(
            @PathVariable Long id, @Valid @RequestBody OrderDtos.AdminNoteRequest request) {
        return DataResponse.of(orderService.setAdminNote(id, request.note()));
    }

    @Operation(summary = "Sifarişin ödənişləri")
    @GetMapping("/{id}/payments")
    public DataResponse<List<OrderDtos.PaymentDto>> payments(@PathVariable Long id) {
        List<OrderDtos.PaymentDto> payments = paymentService.forOrder(id).stream()
                .map(OrderController::toDto)
                .toList();
        return DataResponse.of(payments);
    }

    @Operation(summary = "Məbləği geri qaytarmaq",
            description = "amount göndərilməzsə tam məbləğ qaytarılır.")
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> refund(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) OrderDtos.RefundRequest request) {
        paymentService.refund(id,
                request == null ? null : request.amount(),
                request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }

    private static OrderDtos.PaymentDto toDto(Payment payment) {
        return new OrderDtos.PaymentDto(
                payment.getId(), payment.getProvider(), payment.getReference(),
                payment.getExternalTransactionId(), payment.getAmount(), payment.getRefundedAmount(),
                payment.getStatus().name(), payment.getCardMask(), payment.getFailureMessage(),
                payment.getCreatedAt(), payment.getCompletedAt());
    }
}
