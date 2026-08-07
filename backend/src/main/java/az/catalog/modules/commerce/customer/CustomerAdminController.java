package az.catalog.modules.commerce.customer;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Müştərilər")
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class CustomerAdminController {

    private final CustomerAdminService service;

    @Operation(summary = "Müştəri siyahısı")
    @GetMapping
    public PageResponse<CustomerDtos.CustomerRow> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Customer.Status status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(search, status, pageable));
    }

    @Operation(summary = "Müştəri detalı",
            description = "Profil, ünvanlar, son sifarişlər və ümumi göstəricilər.")
    @GetMapping("/{id}")
    public DataResponse<CustomerAdminService.CustomerDetail> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Statusu dəyişmək",
            description = "BLOCKED və ya INACTIVE edildikdə bütün aktiv sessiyalar dərhal bağlanır.")
    @PatchMapping("/{id}/status")
    public DataResponse<CustomerAdminService.CustomerDetail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody CustomerDtos.StatusRequest request) {
        return DataResponse.of(service.changeStatus(id, request.status()));
    }

    @Operation(summary = "Müştərini silmək (soft delete)",
            description = "Sifariş tarixçəsi qorunur — sifarişlər öz snapshot-ları ilə saxlanılır.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
