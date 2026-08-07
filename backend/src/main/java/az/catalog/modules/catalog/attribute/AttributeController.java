package az.catalog.modules.catalog.attribute;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Attributlar")
@RestController
@RequestMapping("/api/admin/attributes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class AttributeController {

    private final AttributeService service;

    @Operation(summary = "Attribut siyahısı",
            description = "Məhsul redaktoru bunu yükləyib hansı xüsusiyyət inputlarını göstərəcəyini müəyyən edir.")
    @GetMapping
    public DataResponse<List<AttributeDtos.AttributeResponse>> list(
            @RequestParam(required = false) ContentStatus status,
            @RequestLanguage String language) {
        return DataResponse.of(service.list(status, language));
    }

    @Operation(summary = "Attribut detalı")
    @GetMapping("/{id}")
    public DataResponse<AttributeDtos.AttributeResponse> get(@PathVariable Long id,
                                                             @RequestLanguage String language) {
        return DataResponse.of(service.get(id, language));
    }

    @Operation(summary = "Attribut yaratmaq")
    @PostMapping
    public DataResponse<AttributeDtos.AttributeResponse> create(
            @Valid @RequestBody AttributeDtos.SaveRequest request, @RequestLanguage String language) {
        return DataResponse.of(service.create(request, language));
    }

    @Operation(summary = "Attributu redaktə etmək",
            description = "İstifadədə olan attributun data tipi dəyişdirilə bilməz.")
    @PutMapping("/{id}")
    public DataResponse<AttributeDtos.AttributeResponse> update(
            @PathVariable Long id, @Valid @RequestBody AttributeDtos.SaveRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.update(id, request, language));
    }

    @Operation(summary = "Status dəyişmək")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                             @Valid @RequestBody AttributeDtos.StatusRequest request) {
        service.changeStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Attributu silmək", description = "İstifadədə olan attribut silinmir.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- options ------------------------------------------------------------

    @Operation(summary = "Seçim əlavə etmək")
    @PostMapping("/{id}/options")
    public DataResponse<AttributeDtos.AttributeResponse> addOption(
            @PathVariable Long id, @Valid @RequestBody AttributeDtos.OptionRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.addOption(id, request, language));
    }

    @Operation(summary = "Seçimi dəyişmək")
    @PutMapping("/{id}/options/{optionId}")
    public DataResponse<AttributeDtos.AttributeResponse> updateOption(
            @PathVariable Long id, @PathVariable Long optionId,
            @Valid @RequestBody AttributeDtos.OptionRequest request,
            @RequestLanguage String language) {
        return DataResponse.of(service.updateOption(id, optionId, request, language));
    }

    @Operation(summary = "Seçimi silmək", description = "İstifadədə olan seçim silinmir.")
    @DeleteMapping("/{id}/options/{optionId}")
    public DataResponse<AttributeDtos.AttributeResponse> deleteOption(
            @PathVariable Long id, @PathVariable Long optionId, @RequestLanguage String language) {
        return DataResponse.of(service.deleteOption(id, optionId, language));
    }

    // --- groups -------------------------------------------------------------

    @Operation(summary = "Attribut qrupları")
    @GetMapping("/groups")
    public DataResponse<List<AttributeDtos.GroupResponse>> groups(@RequestLanguage String language) {
        return DataResponse.of(service.groups(language));
    }

    @Operation(summary = "Qrup yaratmaq")
    @PostMapping("/groups")
    public DataResponse<AttributeDtos.GroupResponse> createGroup(
            @Valid @RequestBody AttributeDtos.GroupRequest request, @RequestLanguage String language) {
        return DataResponse.of(service.createGroup(request, language));
    }
}
