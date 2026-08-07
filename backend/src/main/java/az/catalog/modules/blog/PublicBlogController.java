package az.catalog.modules.blog;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Public · Bloq")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicBlogController {

    private final BlogService service;

    @Operation(summary = "Bloq siyahısı",
            description = "Yalnız dərc olunmuş və dərc tarixi keçmiş yazılar qaytarılır.")
    @GetMapping("/posts")
    public PageResponse<BlogDtos.PostCard> list(
            @RequestLanguage String language,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 12, sort = "publishedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(service.publicList(category, tag, search, language, pageable));
    }

    @Operation(summary = "Bloq yazısı")
    @GetMapping("/posts/{slug}")
    public DataResponse<BlogDtos.PostDetail> detail(@PathVariable String slug,
                                                    @RequestLanguage String language) {
        return DataResponse.of(service.publicDetail(slug, language));
    }

    @Operation(summary = "Seçilmiş yazılar")
    @GetMapping("/posts/featured")
    public DataResponse<List<BlogDtos.PostCard>> featured(
            @RequestLanguage String language,
            @RequestParam(defaultValue = "3") int limit) {
        return DataResponse.of(service.featured(language, limit));
    }

    @Operation(summary = "Əlaqəli yazılar",
            description = "Eyni kateqoriyadan başlayır, çatmasa son yazılarla tamamlanır.")
    @GetMapping("/posts/{slug}/related")
    public DataResponse<List<BlogDtos.PostCard>> related(
            @PathVariable String slug,
            @RequestLanguage String language,
            @RequestParam(defaultValue = "3") int limit) {
        return DataResponse.of(service.related(slug, language, limit));
    }

    @Operation(summary = "Bloq kateqoriyaları")
    @GetMapping("/post-categories")
    public DataResponse<List<BlogDtos.CategoryDto>> categories(@RequestLanguage String language) {
        return DataResponse.of(service.publicCategories(language));
    }

    @Operation(summary = "Aktiv teqlər", description = "Yalnız ən azı bir canlı yazıda işlənən teqlər.")
    @GetMapping("/tags")
    public DataResponse<List<BlogDtos.TagDto>> tags(@RequestLanguage String language) {
        return DataResponse.of(service.publicTags(language));
    }
}
