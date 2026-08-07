package az.catalog.modules.siteconfig;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Sayt konfiqurasiyası")
@RestController
@RequestMapping("/api/admin/site-configuration")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class SiteConfigurationController {

    private final SiteConfigurationService service;

    @Operation(summary = "Aktiv (dərc olunmuş) versiya")
    @GetMapping("/published")
    public DataResponse<SiteConfigDtos.VersionResponse> published() {
        return DataResponse.of(service.published());
    }

    @Operation(summary = "Cari draft")
    @GetMapping("/draft")
    public DataResponse<SiteConfigDtos.VersionResponse> draft() {
        return DataResponse.of(service.draft());
    }

    @Operation(summary = "Draft yaratmaq",
            description = "Dərc olunmuş versiyanın surətindən yeni draft açır. Draft artıq varsa onu qaytarır.")
    @PostMapping("/draft")
    public DataResponse<SiteConfigDtos.VersionResponse> createDraft(
            @Valid @RequestBody(required = false) SiteConfigDtos.CreateDraftRequest request) {
        return DataResponse.of(service.createDraft(request == null ? null : request.label()));
    }

    @Operation(summary = "Draft-ı dəyişmək",
            description = "Göndərilən JSON fraqmenti mövcud draft-a dərin birləşdirilir (deep merge).")
    @PutMapping("/draft")
    public DataResponse<SiteConfigDtos.VersionResponse> updateDraft(
            @Valid @RequestBody SiteConfigDtos.UpdateDraftRequest request) {
        return DataResponse.of(service.updateDraft(request));
    }

    @Operation(summary = "Draft-ı ləğv etmək")
    @DeleteMapping("/draft")
    public ResponseEntity<Void> discardDraft() {
        service.discardDraft();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Preview məlumatı",
            description = "Draft dərc olunsa saytın necə görünəcəyini qaytarır. Public saytı dəyişmir.")
    @GetMapping("/preview")
    public DataResponse<JsonNode> preview() {
        return DataResponse.of(service.preview());
    }

    @Operation(summary = "Draft-ı dərc etmək")
    @PostMapping("/publish")
    public DataResponse<SiteConfigDtos.VersionResponse> publish() {
        return DataResponse.of(service.publish());
    }

    @Operation(summary = "Versiya siyahısı")
    @GetMapping("/versions")
    public PageResponse<SiteConfigDtos.VersionSummary> versions(
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.versions(pageable));
    }

    @Operation(summary = "Versiya detalı")
    @GetMapping("/versions/{id}")
    public DataResponse<SiteConfigDtos.VersionResponse> version(@PathVariable Long id) {
        return DataResponse.of(service.version(id));
    }

    @Operation(summary = "Köhnə versiyanı draft kimi bərpa etmək",
            description = "Birbaşa dərc etmir — köhnə versiyadan yeni draft yaradır.")
    @PostMapping("/versions/{id}/restore")
    public DataResponse<SiteConfigDtos.VersionResponse> restore(@PathVariable Long id) {
        return DataResponse.of(service.restore(id));
    }
}
