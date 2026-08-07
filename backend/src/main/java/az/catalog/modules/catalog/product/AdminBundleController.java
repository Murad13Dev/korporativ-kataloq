package az.catalog.modules.catalog.product;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Bundle-lar")
@RestController
@RequestMapping("/api/admin/bundles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class AdminBundleController {

    private final ProductBundleService service;

    @Operation(summary = "Bundle siyahısı")
    @GetMapping
    public PageResponse<ProductDtos.BundleAdminDto> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(service.adminSearch(search, pageable));
    }

    @Operation(summary = "Bundle yaratmaq")
    @PostMapping
    public DataResponse<ProductDtos.BundleAdminDto> create(@Valid @RequestBody ProductDtos.SaveBundleRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Bundle-ı redaktə etmək")
    @PutMapping("/{id}")
    public DataResponse<ProductDtos.BundleAdminDto> update(
            @PathVariable Long id, @Valid @RequestBody ProductDtos.SaveBundleRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Bundle-ı silmək")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
