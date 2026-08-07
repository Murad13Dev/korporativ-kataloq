package az.catalog.modules.page;

import az.catalog.common.config.RequestLanguage;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Səhifələr",
        description = "Versiyalı səhifələr: draft → dərc → arxiv. Bölmələr hazır komponent registrindən seçilir.")
@RestController
@RequestMapping("/api/admin/pages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class PageController {

    private final PageService service;

    // --- pages --------------------------------------------------------------

    @Operation(summary = "Səhifə siyahısı", description = "Hər dil üzrə dərc vəziyyəti ilə birlikdə.")
    @GetMapping
    public DataResponse<List<PageDtos.PageRow>> list() {
        return DataResponse.of(service.list());
    }

    @Operation(summary = "Səhifə məlumatı")
    @GetMapping("/{id}")
    public DataResponse<PageDtos.PageResponse> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Yeni səhifə", description = "İlk draft versiya səhifə ilə birlikdə yaradılır.")
    @PostMapping
    public DataResponse<PageDtos.PageResponse> create(
            @Valid @RequestBody PageDtos.CreatePageRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Səhifə parametrlərini dəyişmək")
    @PutMapping("/{id}")
    public DataResponse<PageDtos.PageResponse> update(
            @PathVariable Long id, @Valid @RequestBody PageDtos.UpdatePageRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Səhifəni silmək", description = "Sistem səhifələri silinmir.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- versions -----------------------------------------------------------

    @Operation(summary = "Redaktə ediləcək versiya",
            description = "Açıq draft varsa onu, yoxsa dərc olunmuş versiyanı qaytarır.")
    @GetMapping("/{id}/editable")
    public DataResponse<PageDtos.VersionResponse> editable(@PathVariable Long id,
                                                           @RequestLanguage String language) {
        return DataResponse.of(service.editableVersion(id, language));
    }

    @Operation(summary = "Versiya siyahısı")
    @GetMapping("/{id}/versions")
    public DataResponse<List<PageDtos.VersionResponse>> versions(@PathVariable Long id) {
        return DataResponse.of(service.versions(id));
    }

    @Operation(summary = "Draft versiya yaratmaq",
            description = "Dərc olunmuş versiyanın surətindən açılır. Draft artıq varsa onu qaytarır.")
    @PostMapping("/{id}/versions")
    public DataResponse<PageDtos.VersionResponse> createDraft(
            @PathVariable Long id, @Valid @RequestBody PageDtos.CreateVersionRequest request) {
        return DataResponse.of(service.createDraft(id, request));
    }

    @Operation(summary = "Versiya detalı")
    @GetMapping("/versions/{versionId}")
    public DataResponse<PageDtos.VersionResponse> version(@PathVariable Long versionId) {
        return DataResponse.of(service.version(versionId));
    }

    @Operation(summary = "Draft-ı dəyişmək")
    @PutMapping("/versions/{versionId}")
    public DataResponse<PageDtos.VersionResponse> updateDraft(
            @PathVariable Long versionId, @Valid @RequestBody PageDtos.UpdateVersionRequest request) {
        return DataResponse.of(service.updateDraft(versionId, request));
    }

    @Operation(summary = "Draft-ı dərc etmək")
    @PostMapping("/versions/{versionId}/publish")
    public DataResponse<PageDtos.VersionResponse> publish(@PathVariable Long versionId) {
        return DataResponse.of(service.publish(versionId));
    }

    @Operation(summary = "Draft-ı ləğv etmək")
    @DeleteMapping("/versions/{versionId}")
    public ResponseEntity<Void> discardDraft(@PathVariable Long versionId) {
        service.discardDraft(versionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Köhnə versiyanı draft kimi bərpa etmək",
            description = "Birbaşa dərc etmir — yeni draft yaradır.")
    @PostMapping("/versions/{versionId}/restore")
    public DataResponse<PageDtos.VersionResponse> restore(@PathVariable Long versionId) {
        return DataResponse.of(service.restore(versionId));
    }

    // --- sections -----------------------------------------------------------

    @Operation(summary = "Bölmə registri",
            description = "Mövcud bölmə tipləri, onların variantları və istifadə etdikləri sahələr.")
    @GetMapping("/section-types")
    public DataResponse<List<PageDtos.SectionTypeInfo>> sectionTypes() {
        return DataResponse.of(service.sectionRegistry());
    }

    @Operation(summary = "Bölmə əlavə etmək")
    @PostMapping("/versions/{versionId}/sections")
    public DataResponse<PageDtos.VersionResponse> addSection(
            @PathVariable Long versionId, @Valid @RequestBody PageDtos.SaveSectionRequest request) {
        return DataResponse.of(service.addSection(versionId, request));
    }

    @Operation(summary = "Bölməni redaktə etmək")
    @PutMapping("/versions/{versionId}/sections/{sectionId}")
    public DataResponse<PageDtos.VersionResponse> updateSection(
            @PathVariable Long versionId, @PathVariable Long sectionId,
            @Valid @RequestBody PageDtos.SaveSectionRequest request) {
        return DataResponse.of(service.updateSection(versionId, sectionId, request));
    }

    @Operation(summary = "Bölməni silmək")
    @DeleteMapping("/versions/{versionId}/sections/{sectionId}")
    public DataResponse<PageDtos.VersionResponse> removeSection(
            @PathVariable Long versionId, @PathVariable Long sectionId) {
        return DataResponse.of(service.removeSection(versionId, sectionId));
    }

    @Operation(summary = "Bölməni göstər / gizlət")
    @PatchMapping("/versions/{versionId}/sections/{sectionId}/status")
    public DataResponse<PageDtos.VersionResponse> setSectionActive(
            @PathVariable Long versionId, @PathVariable Long sectionId,
            @Valid @RequestBody PageDtos.ActiveRequest request) {
        return DataResponse.of(service.setSectionActive(versionId, sectionId, request.active()));
    }

    @Operation(summary = "Bölməni kopyalamaq")
    @PostMapping("/versions/{versionId}/sections/{sectionId}/duplicate")
    public DataResponse<PageDtos.VersionResponse> duplicateSection(
            @PathVariable Long versionId, @PathVariable Long sectionId) {
        return DataResponse.of(service.duplicateSection(versionId, sectionId));
    }

    @Operation(summary = "Bölmələri sıralamaq")
    @PutMapping("/versions/{versionId}/sections/reorder")
    public DataResponse<PageDtos.VersionResponse> reorderSections(
            @PathVariable Long versionId, @Valid @RequestBody PageDtos.ReorderRequest request) {
        return DataResponse.of(service.reorderSections(versionId, request));
    }
}
