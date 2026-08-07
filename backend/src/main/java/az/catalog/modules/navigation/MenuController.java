package az.catalog.modules.navigation;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Naviqasiya",
        description = "Versiyalı menyular. Dəyişikliklər dərc olunana qədər saytda görünmür.")
@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class MenuController {

    private final MenuService service;

    @Operation(summary = "Menyu siyahısı")
    @GetMapping
    public DataResponse<List<MenuDtos.MenuRow>> list() {
        return DataResponse.of(service.list());
    }

    @Operation(summary = "Menyu yaratmaq")
    @PostMapping
    public DataResponse<MenuDtos.MenuRow> create(@Valid @RequestBody MenuDtos.SaveMenuRequest request) {
        return DataResponse.of(service.create(request));
    }

    @Operation(summary = "Menyunu dəyişmək")
    @PutMapping("/{id}")
    public DataResponse<MenuDtos.MenuRow> update(
            @PathVariable Long id, @Valid @RequestBody MenuDtos.SaveMenuRequest request) {
        return DataResponse.of(service.update(id, request));
    }

    @Operation(summary = "Menyunu silmək")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- versions -----------------------------------------------------------

    @Operation(summary = "Redaktə ediləcək versiya",
            description = "Açıq draft varsa onu, yoxsa dərc olunmuş versiyanı qaytarır.")
    @GetMapping("/{id}/editable")
    public DataResponse<MenuDtos.VersionResponse> editable(@PathVariable Long id,
                                                           @RequestLanguage String language) {
        return DataResponse.of(service.editableVersion(id, language));
    }

    @Operation(summary = "Draft yaratmaq", description = "Dərc olunmuş menyunun surətindən açılır.")
    @PostMapping("/{id}/draft")
    public DataResponse<MenuDtos.VersionResponse> createDraft(@PathVariable Long id,
                                                              @RequestLanguage String language) {
        return DataResponse.of(service.createDraft(id, language));
    }

    @Operation(summary = "Draft-ı dərc etmək")
    @PostMapping("/{id}/publish")
    public DataResponse<MenuDtos.VersionResponse> publish(@PathVariable Long id,
                                                          @RequestLanguage String language) {
        return DataResponse.of(service.publish(id, language));
    }

    @Operation(summary = "Draft-ı ləğv etmək")
    @DeleteMapping("/{id}/draft")
    public ResponseEntity<Void> discardDraft(@PathVariable Long id) {
        service.discardDraft(id);
        return ResponseEntity.noContent().build();
    }

    // --- items --------------------------------------------------------------

    @Operation(summary = "Element əlavə etmək")
    @PostMapping("/versions/{versionId}/items")
    public DataResponse<MenuDtos.VersionResponse> addItem(
            @PathVariable Long versionId, @Valid @RequestBody MenuDtos.SaveItemRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.addItem(versionId, request, language));
    }

    @Operation(summary = "Elementi dəyişmək")
    @PutMapping("/versions/{versionId}/items/{itemId}")
    public DataResponse<MenuDtos.VersionResponse> updateItem(
            @PathVariable Long versionId, @PathVariable Long itemId,
            @Valid @RequestBody MenuDtos.SaveItemRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.updateItem(versionId, itemId, request, language));
    }

    @Operation(summary = "Elementi silmək", description = "Alt elementlər də silinir.")
    @DeleteMapping("/versions/{versionId}/items/{itemId}")
    public DataResponse<MenuDtos.VersionResponse> removeItem(
            @PathVariable Long versionId, @PathVariable Long itemId,
            @RequestLanguage String language) {
        return DataResponse.of(service.removeItem(versionId, itemId, language));
    }

    @Operation(summary = "Sıralama və yenidən qruplaşdırma",
            description = "Drag-and-drop nəticəsini bir sorğuda tətbiq edir; dövrə və dərinlik yoxlanılır.")
    @PutMapping("/versions/{versionId}/items/reorder")
    public DataResponse<MenuDtos.VersionResponse> reorder(
            @PathVariable Long versionId, @Valid @RequestBody MenuDtos.ReorderRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.reorder(versionId, request, language));
    }
}
