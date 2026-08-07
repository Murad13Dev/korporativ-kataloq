package az.catalog.modules.catalog.product;

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

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Admin · Məhsullar")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class ProductController {

    private final ProductService service;

    @Operation(summary = "Məhsul siyahısı")
    @GetMapping
    public PageResponse<ProductDtos.ProductRow> list(
            @RequestLanguage String language,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean discounted,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        ProductFilter filter = new ProductFilter(search, categoryId, null, featured, null,
                discounted, inStock, lowStock, minPrice, maxPrice, null, null, status);
        return PageResponse.of(service.adminSearch(filter, language, pageable));
    }

    @Operation(summary = "Məhsul detalı")
    @GetMapping("/{id}")
    public DataResponse<ProductDtos.ProductResponse> get(@PathVariable Long id,
                                                         @RequestLanguage String language) {
        return DataResponse.of(service.get(id, language));
    }

    @Operation(summary = "Məhsul yaratmaq")
    @PostMapping
    public DataResponse<ProductDtos.ProductResponse> create(
            @Valid @RequestBody ProductDtos.SaveRequest request, @RequestLanguage String language) {
        return DataResponse.of(service.create(request, language));
    }

    @Operation(summary = "Məhsulu redaktə etmək")
    @PutMapping("/{id}")
    public DataResponse<ProductDtos.ProductResponse> update(
            @PathVariable Long id, @Valid @RequestBody ProductDtos.SaveRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.update(id, request, language));
    }

    @Operation(summary = "Status dəyişmək")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody ProductDtos.StatusRequest request) {
        service.changeStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Featured dəyişmək")
    @PatchMapping("/{id}/featured")
    public ResponseEntity<Void> changeFeatured(@PathVariable Long id,
                                               @Valid @RequestBody ProductDtos.FeaturedRequest request) {
        service.changeFeatured(id, request.featured());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Anbar sayını dəyişmək")
    @PatchMapping("/{id}/stock")
    public ResponseEntity<Void> adjustStock(@PathVariable Long id,
                                            @Valid @RequestBody ProductDtos.StockRequest request) {
        service.adjustStock(id, request.stockQuantity());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Məhsulu silmək (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Məhsulu kopyalamaq", description = "Kopya həmişə DRAFT statusunda yaradılır.")
    @PostMapping("/{id}/duplicate")
    public DataResponse<ProductDtos.ProductResponse> duplicate(@PathVariable Long id,
                                                               @RequestLanguage String language) {
        return DataResponse.of(service.duplicate(id, language));
    }

    // --- images -------------------------------------------------------------

    @Operation(summary = "Şəkil əlavə etmək")
    @PostMapping("/{id}/images")
    public DataResponse<List<ProductDtos.ImageDto>> addImage(
            @PathVariable Long id, @Valid @RequestBody ProductDtos.AddImageRequest request) {
        return DataResponse.of(service.addImage(id, request));
    }

    @Operation(summary = "Əsas şəkli seçmək")
    @PatchMapping("/{id}/images/{imageId}/main")
    public DataResponse<List<ProductDtos.ImageDto>> setMainImage(@PathVariable Long id,
                                                                 @PathVariable Long imageId) {
        return DataResponse.of(service.setMainImage(id, imageId));
    }

    @Operation(summary = "Şəkilləri sıralamaq")
    @PutMapping("/{id}/images/reorder")
    public DataResponse<List<ProductDtos.ImageDto>> reorderImages(
            @PathVariable Long id, @Valid @RequestBody ProductDtos.ReorderRequest request) {
        return DataResponse.of(service.reorderImages(id, request));
    }

    @Operation(summary = "Şəkli silmək")
    @DeleteMapping("/{id}/images/{imageId}")
    public DataResponse<List<ProductDtos.ImageDto>> removeImage(@PathVariable Long id,
                                                                @PathVariable Long imageId) {
        return DataResponse.of(service.removeImage(id, imageId));
    }

    // --- documents ----------------------------------------------------------

    @Operation(summary = "Sənəd əlavə etmək")
    @PostMapping("/{id}/documents")
    public DataResponse<List<ProductDtos.DocumentDto>> addDocument(
            @PathVariable Long id, @Valid @RequestBody ProductDtos.AddDocumentRequest request) {
        return DataResponse.of(service.addDocument(id, request));
    }

    @Operation(summary = "Sənədi silmək")
    @DeleteMapping("/{id}/documents/{documentId}")
    public DataResponse<List<ProductDtos.DocumentDto>> removeDocument(@PathVariable Long id,
                                                                      @PathVariable Long documentId) {
        return DataResponse.of(service.removeDocument(id, documentId));
    }

    // --- variants -------------------------------------------------------------

    @Operation(summary = "Variant siyahısı")
    @GetMapping("/{id}/variants")
    public DataResponse<List<ProductDtos.VariantAdminDto>> listVariants(@PathVariable Long id) {
        return DataResponse.of(service.listVariants(id));
    }

    @Operation(summary = "Variant yaratmaq")
    @PostMapping("/{id}/variants")
    public DataResponse<List<ProductDtos.VariantAdminDto>> createVariant(
            @PathVariable Long id, @Valid @RequestBody ProductDtos.SaveVariantRequest request) {
        return DataResponse.of(service.createVariant(id, request));
    }

    @Operation(summary = "Variantı redaktə etmək")
    @PutMapping("/{id}/variants/{variantId}")
    public DataResponse<List<ProductDtos.VariantAdminDto>> updateVariant(
            @PathVariable Long id, @PathVariable Long variantId,
            @Valid @RequestBody ProductDtos.SaveVariantRequest request) {
        return DataResponse.of(service.updateVariant(id, variantId, request));
    }

    @Operation(summary = "Variantı silmək")
    @DeleteMapping("/{id}/variants/{variantId}")
    public DataResponse<List<ProductDtos.VariantAdminDto>> deleteVariant(
            @PathVariable Long id, @PathVariable Long variantId) {
        return DataResponse.of(service.deleteVariant(id, variantId));
    }
}
