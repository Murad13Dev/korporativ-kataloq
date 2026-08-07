package az.catalog.modules.catalog.category;

import az.catalog.common.domain.ContentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** Storefront shape: already resolved to one language. */
    public record CategoryCard(
            Long id,
            String slug,
            String name,
            String description,
            String icon,
            String imageUrl,
            Long productCount,
            boolean featured
    ) {
    }

    /** Nested node for the tree endpoints. */
    public record CategoryNode(
            Long id,
            Long parentId,
            String slug,
            String name,
            String icon,
            String imageUrl,
            ContentStatus status,
            boolean featured,
            boolean showInMenu,
            int depth,
            int sortOrder,
            Long productCount,
            List<CategoryNode> children
    ) {
    }

    /** Admin detail with every translation exposed for the language tabs. */
    public record CategoryResponse(
            Long id,
            Long parentId,
            String slug,
            Long imageMediaId,
            String imageUrl,
            Long iconMediaId,
            Long bannerMediaId,
            String icon,
            ContentStatus status,
            boolean featured,
            boolean showInMenu,
            String path,
            int depth,
            int sortOrder,
            Long productCount,
            Map<String, TranslationDto> translations
    ) {
    }

    public record TranslationDto(
            @NotBlank @Size(max = 255) String name,
            String description,
            @Size(max = 255) String seoTitle,
            @Size(max = 500) String seoDescription
    ) {
    }

    public record SaveRequest(
            Long parentId,
            @Size(max = 180) String slug,
            Long imageMediaId,
            Long iconMediaId,
            Long bannerMediaId,
            @Size(max = 64) String icon,
            ContentStatus status,
            Boolean featured,
            Boolean showInMenu,
            Integer sortOrder,

            /** Keyed by language code. The default language entry is mandatory. */
            @NotEmpty(message = "Ən azı bir dil üçün tərcümə tələb olunur")
            Map<String, @Valid TranslationDto> translations
    ) {
    }

    public record StatusRequest(@NotNull ContentStatus status) {
    }

    public record ReorderRequest(@NotEmpty List<@Valid ReorderItem> items) {
    }

    public record ReorderItem(
            @NotNull Long id,
            Long parentId,
            @NotNull Integer sortOrder
    ) {
    }
}
