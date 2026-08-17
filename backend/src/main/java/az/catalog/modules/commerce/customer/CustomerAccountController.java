package az.catalog.modules.commerce.customer;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import az.catalog.common.security.SecurityUtils;
import az.catalog.modules.commerce.cart.CartDtos;
import az.catalog.modules.commerce.order.OrderDtos;
import az.catalog.modules.commerce.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customer · Kabinet")
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerAccountController {

    private final AddressService addressService;
    private final OrderService orderService;

    // --- addresses ----------------------------------------------------------

    @Operation(summary = "Ünvanlar")
    @GetMapping("/addresses")
    public DataResponse<List<CustomerDtos.AddressResponse>> addresses() {
        return DataResponse.of(addressService.list(SecurityUtils.requireCustomer().id()));
    }

    @Operation(summary = "Ünvan əlavə etmək")
    @PostMapping("/addresses")
    public DataResponse<CustomerDtos.AddressResponse> createAddress(
            @Valid @RequestBody CustomerDtos.AddressRequest request) {
        return DataResponse.of(addressService.create(SecurityUtils.requireCustomer().id(), request));
    }

    @Operation(summary = "Ünvanı dəyişmək")
    @PutMapping("/addresses/{id}")
    public DataResponse<CustomerDtos.AddressResponse> updateAddress(
            @PathVariable Long id, @Valid @RequestBody CustomerDtos.AddressRequest request) {
        return DataResponse.of(addressService.update(SecurityUtils.requireCustomer().id(), id, request));
    }

    @Operation(summary = "Əsas ünvan seçmək")
    @PatchMapping("/addresses/{id}/default")
    public DataResponse<CustomerDtos.AddressResponse> makeDefault(@PathVariable Long id) {
        return DataResponse.of(addressService.makeDefault(SecurityUtils.requireCustomer().id(), id));
    }

    @Operation(summary = "Ünvanı silmək")
    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        addressService.delete(SecurityUtils.requireCustomer().id(), id);
        return ResponseEntity.noContent().build();
    }

    // --- orders -------------------------------------------------------------

    @Operation(summary = "Sifariş tarixçəsi")
    @GetMapping("/orders")
    public PageResponse<OrderDtos.OrderSummary> orders(@PageableDefault(size = 10) Pageable pageable) {
        return PageResponse.of(
                orderService.customerOrders(SecurityUtils.requireCustomer().id(), pageable));
    }

    @Operation(summary = "Sifariş detalı")
    @GetMapping("/orders/{id}")
    public DataResponse<OrderDtos.OrderDetail> order(@PathVariable Long id) {
        return DataResponse.of(
                orderService.getForCustomer(id, SecurityUtils.requireCustomer().id()));
    }

    @Operation(summary = "Sifarişi ləğv etmək",
            description = "Yalnız ödəniş edilməmiş və göndərilməmiş sifarişlər ləğv edilə bilər.")
    @PostMapping("/orders/{id}/cancel")
    public DataResponse<OrderDtos.OrderDetail> cancelOrder(
            @PathVariable Long id, @RequestBody(required = false) OrderDtos.CancelRequest request) {
        return DataResponse.of(orderService.cancelByCustomer(
                id, SecurityUtils.requireCustomer().id(), request == null ? null : request.reason()));
    }

    @Operation(summary = "Yenidən sifariş et",
            description = "Keçmiş sifarişin bütün sətirlərini bugünkü qiymətlərlə cari səbətə əlavə edir.")
    @PostMapping("/orders/{id}/reorder")
    public DataResponse<CartDtos.BulkAddResponse> reorder(
            @PathVariable Long id, @RequestLanguage String language) {
        return DataResponse.of(orderService.reorder(id, SecurityUtils.requireCustomer().id(), language));
    }

    @Operation(summary = "Qənaət kabineti",
            description = "Kampaniya, promo kod və korporativ endirimlərdən yaranan ümumi qənaət — ömürlük və bu il.")
    @GetMapping("/savings")
    public DataResponse<OrderDtos.SavingsSummary> savings() {
        return DataResponse.of(orderService.savingsSummary(SecurityUtils.requireCustomer().id()));
    }
}
