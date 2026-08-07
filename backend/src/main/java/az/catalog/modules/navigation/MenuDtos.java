package az.catalog.modules.navigation;

import az.catalog.common.domain.VersionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class MenuDtos {

    private MenuDtos() {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public record MenuRow(
            Long id,
            String code,
            String name,
            Menu.Location location,
            String locationLabel,
            boolean active,
            int itemCount,
            boolean hasDraft,
            OffsetDateTime publishedAt
    ) {
    }

    public record VersionResponse(
            Long id,
            Long menuId,
            String menuCode,
            String menuName,
            Menu.Location location,
            Integer versionNumber,
            VersionStatus status,
            OffsetDateTime publishedAt,
            /** Nested tree, already assembled. */
            List<ItemResponse> items
    ) {
    }

    public record ItemResponse(
            Long id,
            Long parentId,
            String title,
            String url,
            MenuItem.LinkType linkType,
            Long referenceId,
            String icon,
            String description,
            String target,
            boolean highlight,
            int sortOrder,
            boolean active,
            Map<String, TranslationDto> translations,
            List<ItemResponse> children
    ) {
    }

    public record TranslationDto(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 255) String description
    ) {
    }

    // --- requests -----------------------------------------------------------

    public record SaveMenuRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{2,64}",
                    message = "Kod kiçik hərf, rəqəm və tire ola bilər")
            String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull Menu.Location location,
            Boolean active
    ) {
    }

    public record SaveItemRequest(
            Long parentId,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 500) String url,
            MenuItem.LinkType linkType,
            Long referenceId,
            @Size(max = 64) String icon,
            @Size(max = 255) String description,
            @Pattern(regexp = "_self|_blank") String target,
            Boolean highlight,
            Integer sortOrder,
            Boolean active,
            /** Per-language labels; the default language falls back to `title`. */
            Map<String, @Valid TranslationDto> translations
    ) {
    }

    /**
     * A drag-and-drop result: every moved item with its new parent and position,
     * applied in one transaction so the tree can never be observed half-rearranged.
     */
    public record ReorderRequest(@NotEmpty List<@Valid ReorderItem> items) {
    }

    public record ReorderItem(
            @NotNull Long id,
            Long parentId,
            @NotNull Integer sortOrder
    ) {
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    public record PublicMenuItem(
            Long id,
            String title,
            String url,
            String linkType,
            String icon,
            String description,
            String target,
            boolean highlight,
            List<PublicMenuItem> children
    ) {
    }
}
