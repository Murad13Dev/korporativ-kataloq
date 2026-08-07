package az.catalog.modules.catalog.category;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.domain.ContentStatus;
import az.catalog.common.dto.ApiResponses.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Kateqoriyalar")
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class CategoryController {

    private final CategoryService service;

    @Operation(summary = "Kateqoriya ağacı", description = "Bütün statuslar daxil olmaqla iyerarxiya.")
    @GetMapping("/tree")
    public DataResponse<List<CategoryDtos.CategoryNode>> tree(@RequestLanguage String language) {
        return DataResponse.of(service.tree(language, false));
    }

    @Operation(summary = "Kateqoriya detalı")
    @GetMapping("/{id}")
    public DataResponse<CategoryDtos.CategoryResponse> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Kateqoriya yaratmaq")
    @PostMapping
    public DataResponse<CategoryDtos.CategoryResponse> create(
            @Valid @RequestBody CategoryDtos.SaveRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Kateqoriyanı redaktə etmək")
    @PutMapping("/{id}")
    public DataResponse<CategoryDtos.CategoryResponse> update(
            @PathVariable Long id, @Valid @RequestBody CategoryDtos.SaveRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Status dəyişmək",
            description = "Deaktiv edilərsə bütün alt kateqoriyalar da gizlədilir.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody CategoryDtos.StatusRequest request) {
        service.changeStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sıralama və yenidən qruplaşdırma",
            description = "Drag-and-drop nəticəsini bir sorğuda tətbiq edir.")
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody CategoryDtos.ReorderRequest request) {
        service.reorder(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kateqoriyanı silmək",
            description = "Alt kateqoriya və ya aktiv məhsul varsa əməliyyat bloklanır.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kateqoriya siyahısı (düz)",
            description = "Seçim siyahıları üçün; yalnız aktiv kateqoriyalar.")
    @GetMapping
    public DataResponse<List<CategoryDtos.CategoryCard>> list(@RequestLanguage String language) {
        return DataResponse.of(service.publishedList(language));
    }

    @Operation(summary = "Statusu dəyişmək üçün alternativ endpoint")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        service.changeStatus(id, ContentStatus.ARCHIVED);
        return ResponseEntity.noContent().build();
    }
}
