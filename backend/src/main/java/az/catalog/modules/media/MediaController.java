package az.catalog.modules.media;

import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Admin · Media")
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EDITOR')")
public class MediaController {

    private final MediaService service;

    @Operation(summary = "Fayl siyahısı")
    @GetMapping
    public PageResponse<MediaDtos.MediaResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MediaFile.MediaType type,
            @RequestParam(required = false) String folder,
            @PageableDefault(size = 40, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(search, type, folder, pageable));
    }

    @Operation(summary = "Fayl yükləmək",
            description = "MIME tipi, uzantı və magic byte-lar yoxlanılır. Eyni fayl təkrar yüklənərsə mövcud qeyd qaytarılır.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DataResponse<MediaDtos.MediaResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "general") String folder,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) String title) {
        return DataResponse.of(service.upload(file, folder, altText, title));
    }

    @Operation(summary = "Fayl məlumatı")
    @GetMapping("/{id}")
    public DataResponse<MediaDtos.MediaResponse> get(@PathVariable Long id) {
        return DataResponse.of(service.get(id));
    }

    @Operation(summary = "Alt text və metadata dəyişmək")
    @PutMapping("/{id}")
    public DataResponse<MediaDtos.MediaResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody MediaDtos.UpdateRequest request) {
        return DataResponse.of(service.updateMetadata(id, request));
    }

    @Operation(summary = "Faylın istifadə sayı",
            description = "Silmədən əvvəl faylın harada işləndiyini yoxlamaq üçün.")
    @GetMapping("/{id}/usage")
    public DataResponse<MediaDtos.UsageResponse> usage(@PathVariable Long id) {
        long count = service.usageCount(id);
        return DataResponse.of(new MediaDtos.UsageResponse(count, count == 0));
    }

    @Operation(summary = "Faylı silmək",
            description = "İstifadədə olan fayl silinmir (MEDIA_FILE_IN_USE). Məcburi silmə endpoint-i yoxdur.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Qovluqlar")
    @GetMapping("/folders")
    public DataResponse<List<String>> folders() {
        return DataResponse.of(service.folders());
    }

    @Operation(summary = "Anbar statistikası")
    @GetMapping("/stats")
    public DataResponse<MediaDtos.StorageStats> stats() {
        return DataResponse.of(service.stats());
    }
}
