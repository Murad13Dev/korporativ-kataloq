package az.catalog.modules.commerce.promo;

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

import java.util.List;
import java.util.Map;

@Tag(name = "Admin · Promo kodlar",
        description = "Səbət səviyyəli kuponlar. Məhsul kampaniyaları ilə birlikdə işləyir.")
@RestController
@RequestMapping("/api/admin/promo-codes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class PromoCodeController {

    private final PromoCodeAdminService service;

    @Operation(summary = "Promo kod siyahısı")
    @GetMapping
    public PageResponse<PromoCodeDtos.PromoCodeResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(search, status, pageable));
    }

    @Operation(summary = "Promo kod detalı")
    @GetMapping("/{id}")
    public DataResponse<PromoCodeDtos.PromoCodeResponse> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Promo kod yaratmaq")
    @PostMapping
    public DataResponse<PromoCodeDtos.PromoCodeResponse> create(
            @Valid @RequestBody PromoCodeDtos.SaveRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Promo kodu redaktə etmək",
            description = "İstifadə olunmuş kodun adı dəyişdirilə bilməz.")
    @PutMapping("/{id}")
    public DataResponse<PromoCodeDtos.PromoCodeResponse> update(
            @PathVariable Long id, @Valid @RequestBody PromoCodeDtos.SaveRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Statusu dəyişmək")
    @PatchMapping("/{id}/status")
    public DataResponse<PromoCodeDtos.PromoCodeResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody PromoCodeDtos.StatusRequest request) {
        return DataResponse.of(service.changeStatus(id, request.status()));
    }

    @Operation(summary = "Promo kodu silmək")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "İstifadə tarixçəsi", description = "Kodun kim tərəfindən nə vaxt işlədildiyi.")
    @GetMapping("/{id}/redemptions")
    public DataResponse<List<PromoCodeDtos.RedemptionResponse>> redemptions(@PathVariable Long id) {
        return DataResponse.of(service.redemptions(id));
    }

    @Operation(summary = "Təsadüfi kod yaratmaq",
            description = "Qarışdırıla bilən simvollar (0/O, 1/I) istifadə olunmur.")
    @GetMapping("/generate")
    public DataResponse<Map<String, String>> generate(@RequestParam(required = false) String prefix) {
        return DataResponse.of(Map.of("code", service.generateCode(prefix)));
    }
}
