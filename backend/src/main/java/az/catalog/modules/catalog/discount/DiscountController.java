package az.catalog.modules.catalog.discount;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.domain.ContentStatus;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Endirimlər", description = "Kampaniyaların yaradılması, planlaşdırılması və lent (badge) tənzimləmələri")
@RestController
@RequestMapping("/api/admin/discounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class DiscountController {

    private final DiscountService service;

    @Operation(summary = "Kampaniya siyahısı")
    @GetMapping
    public PageResponse<DiscountDtos.DiscountSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) DiscountType type,
            @RequestParam(required = false) DiscountScope scope,
            @PageableDefault(size = 20, sort = "priority", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(search, status, type, scope, pageable));
    }

    @Operation(summary = "Kampaniya detalı")
    @GetMapping("/{id}")
    public DataResponse<DiscountDtos.DiscountResponse> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Kampaniya yaratmaq")
    @PostMapping
    public DataResponse<DiscountDtos.DiscountResponse> create(
            @Valid @RequestBody DiscountDtos.SaveRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Kampaniyanı redaktə etmək")
    @PutMapping("/{id}")
    public DataResponse<DiscountDtos.DiscountResponse> update(
            @PathVariable Long id, @Valid @RequestBody DiscountDtos.SaveRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Statusu dəyişmək (aktivləşdir / dayandır)")
    @PatchMapping("/{id}/status")
    public DataResponse<DiscountDtos.DiscountResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody DiscountDtos.StatusRequest request) {
        return DataResponse.of(service.changeStatus(id, request.status()));
    }

    @Operation(summary = "Kampaniya təsirini önizləmək",
            description = "Neçə məhsulun təsirləndiyini və nümunə qiymətləri qaytarır. Heç nəyi dəyişmir.")
    @GetMapping("/{id}/preview")
    public DataResponse<DiscountDtos.PreviewResponse> preview(@PathVariable Long id,
                                                              @RequestLanguage String language) {
        return DataResponse.of(service.preview(id, language));
    }

    @Operation(summary = "Kampaniyanı silmək")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Məhsul üçün sürətli endirim yaratmaq",
            description = "Məhsul redaktə səhifəsindən çağırılır; tək məhsulluq kampaniya yaradır.")
    @PostMapping("/for-product/{productId}")
    public DataResponse<DiscountDtos.DiscountResponse> createForProduct(
            @PathVariable Long productId, @Valid @RequestBody DiscountDtos.SaveRequest request) {
        return DataResponse.of(service.createForProduct(productId, request));
    }
}
