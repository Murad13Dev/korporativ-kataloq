package az.catalog.modules.catalog.review;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Rəylər")
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','SUPPORT')")
public class AdminReviewController {

    private final ProductReviewService service;

    @Operation(summary = "Rəy siyahısı")
    @GetMapping
    public PageResponse<ReviewDtos.ReviewAdminDto> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.adminSearch(productId, status, search, pageable));
    }

    @Operation(summary = "Rəyi təsdiqləmək/rədd etmək")
    @PatchMapping("/{id}/status")
    public DataResponse<ReviewDtos.ReviewAdminDto> moderate(
            @PathVariable Long id, @Valid @RequestBody ReviewDtos.ModerateRequest request) {
        return DataResponse.of(service.moderate(id, request.status()));
    }

    @Operation(summary = "Cavab yazmaq")
    @PutMapping("/{id}/reply")
    public DataResponse<ReviewDtos.ReviewAdminDto> reply(
            @PathVariable Long id, @Valid @RequestBody ReviewDtos.ReplyRequest request) {
        return DataResponse.of(service.reply(id, request.reply()));
    }

    @Operation(summary = "Rəyi silmək")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
