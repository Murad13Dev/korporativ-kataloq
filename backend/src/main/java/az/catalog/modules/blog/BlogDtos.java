package az.catalog.modules.blog;

import az.catalog.common.domain.ContentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class BlogDtos {

    private BlogDtos() {
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    /** Card shape used by the blog list and the LATEST_POSTS section. */
    public record PostCard(
            Long id,
            String slug,
            String title,
            String shortDescription,
            String coverUrl,
            Long categoryId,
            String categorySlug,
            String categoryName,
            String categoryColor,
            boolean featured,
            Integer readingMinutes,
            OffsetDateTime publishedAt
    ) {
    }

    public record PostDetail(
            Long id,
            String slug,
            String title,
            String shortDescription,
            String content,
            String coverUrl,
            String seoTitle,
            String seoDescription,
            String categorySlug,
            String categoryName,
            String authorName,
            Integer readingMinutes,
            long viewCount,
            List<TagDto> tags,
            OffsetDateTime publishedAt
    ) {
    }

    public record CategoryDto(
            Long id,
            String slug,
            String name,
            String description,
            String color,
            long postCount
    ) {
    }

    public record TagDto(Long id, String slug, String name) {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public record PostRow(
            Long id,
            String slug,
            String title,
            String coverUrl,
            String categoryName,
            ContentStatus status,
            boolean featured,
            /** True when ACTIVE but the publish moment is still in the future. */
            boolean scheduled,
            long viewCount,
            OffsetDateTime publishedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PostResponse(
            Long id,
            String slug,
            Long categoryId,
            Long coverMediaId,
            String coverUrl,
            ContentStatus status,
            boolean featured,
            Integer readingMinutes,
            long viewCount,
            OffsetDateTime publishedAt,
            List<Long> tagIds,
            Map<String, TranslationDto> translations
    ) {
    }

    public record TranslationDto(
            @NotBlank @Size(max = 255) String title,
            String shortDescription,
            String content,
            @Size(max = 255) String seoTitle,
            @Size(max = 500) String seoDescription
    ) {
    }

    public record SavePostRequest(
            @Size(max = 180) String slug,
            Long categoryId,
            Long coverMediaId,
            ContentStatus status,
            Boolean featured,
            Integer readingMinutes,
            /** Set to a future instant to schedule; null publishes now on activation. */
            OffsetDateTime publishedAt,
            List<Long> tagIds,

            @NotEmpty(message = "Ən azı bir dil üçün məzmun tələb olunur")
            Map<String, @Valid TranslationDto> translations
    ) {
    }

    public record StatusRequest(@NotNull ContentStatus status) {
    }

    public record FeaturedRequest(@NotNull Boolean featured) {
    }

    // --- taxonomy ---

    public record SaveCategoryRequest(
            @Size(max = 180) String slug,
            @Pattern(regexp = "#[0-9a-fA-F]{3,8}", message = "Rəng #RRGGBB formatında olmalıdır")
            String color,
            ContentStatus status,
            Integer sortOrder,
            @NotEmpty Map<String, @Size(max = 160) String> names
    ) {
    }

    public record SaveTagRequest(
            @NotBlank @Size(max = 64) String code,
            @NotEmpty Map<String, @Size(max = 120) String> names
    ) {
    }
}
