package az.catalog.modules.blog;

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

import java.util.List;

@Tag(name = "Admin · Bloq")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class BlogController {

    private final BlogService service;

    // --- posts --------------------------------------------------------------

    @Operation(summary = "Yazı siyahısı")
    @GetMapping("/posts")
    public PageResponse<BlogDtos.PostRow> list(
            @RequestLanguage String language,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(service.adminSearch(search, status, categoryId, language, pageable));
    }

    @Operation(summary = "Yazı detalı")
    @GetMapping("/posts/{id}")
    public DataResponse<BlogDtos.PostResponse> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Yazı yaratmaq",
            description = "publishedAt gələcək tarixdirsə yazı həmin vaxta planlaşdırılır.")
    @PostMapping("/posts")
    public DataResponse<BlogDtos.PostResponse> create(
            @Valid @RequestBody BlogDtos.SavePostRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Yazını redaktə etmək")
    @PutMapping("/posts/{id}")
    public DataResponse<BlogDtos.PostResponse> update(
            @PathVariable Long id, @Valid @RequestBody BlogDtos.SavePostRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Status dəyişmək")
    @PatchMapping("/posts/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long id, @Valid @RequestBody BlogDtos.StatusRequest request) {
        service.changeStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Featured dəyişmək")
    @PatchMapping("/posts/{id}/featured")
    public ResponseEntity<Void> changeFeatured(
            @PathVariable Long id, @Valid @RequestBody BlogDtos.FeaturedRequest request) {
        service.changeFeatured(id, request.featured());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Yazını silmək (soft delete)")
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- categories ---------------------------------------------------------

    @Operation(summary = "Bloq kateqoriyaları")
    @GetMapping("/post-categories")
    public DataResponse<List<BlogDtos.CategoryDto>> categories(@RequestLanguage String language) {
        return DataResponse.of(service.adminCategories(language));
    }

    @Operation(summary = "Kateqoriya yaratmaq")
    @PostMapping("/post-categories")
    public DataResponse<BlogDtos.CategoryDto> createCategory(
            @Valid @RequestBody BlogDtos.SaveCategoryRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.saveCategory(null, request, language));
    }

    @Operation(summary = "Kateqoriyanı dəyişmək")
    @PutMapping("/post-categories/{id}")
    public DataResponse<BlogDtos.CategoryDto> updateCategory(
            @PathVariable Long id, @Valid @RequestBody BlogDtos.SaveCategoryRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.saveCategory(id, request, language));
    }

    @Operation(summary = "Kateqoriyanı silmək", description = "Yazıları olan kateqoriya silinmir.")
    @DeleteMapping("/post-categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // --- tags ---------------------------------------------------------------

    @Operation(summary = "Teqlər")
    @GetMapping("/tags")
    public DataResponse<List<BlogDtos.TagDto>> tags(@RequestLanguage String language) {
        return DataResponse.of(service.adminTags(language));
    }

    @Operation(summary = "Teq yaratmaq və ya yeniləmək")
    @PostMapping("/tags")
    public DataResponse<BlogDtos.TagDto> saveTag(
            @Valid @RequestBody BlogDtos.SaveTagRequest request, @RequestLanguage String language) {
        return DataResponse.of(service.saveTag(request, language));
    }
}
