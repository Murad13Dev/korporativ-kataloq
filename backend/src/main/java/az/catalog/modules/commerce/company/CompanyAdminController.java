package az.catalog.modules.commerce.company;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Şirkətlər")
@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class CompanyAdminController {

    private final CompanyService service;

    @Operation(summary = "Şirkətlər siyahısı")
    @GetMapping
    public PageResponse<CompanyDtos.CompanyRow> search(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.adminSearch(search, pageable));
    }

    @Operation(summary = "Şirkət detalı", description = "Üzv siyahısı daxildir.")
    @GetMapping("/{id}")
    public DataResponse<CompanyDtos.CompanyView> get(@PathVariable Long id) {
        return DataResponse.of(service.adminGet(id));
    }

    @Operation(summary = "Endirim faizini təyin etmək")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PatchMapping("/{id}/discount")
    public ResponseEntity<Void> setDiscount(@PathVariable Long id,
                                            @Valid @RequestBody CompanyDtos.SetDiscountRequest request) {
        service.setDiscount(id, request.discountPercentage());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Statusu dəyişmək", description = "PENDING → ACTIVE təsdiqdir; SUSPENDED endirimi dərhal dayandırır.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> setStatus(@PathVariable Long id,
                                          @RequestBody CompanyDtos.SetStatusRequest request) {
        service.setStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }
}
