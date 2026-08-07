package az.catalog.modules.page;

import az.catalog.common.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class PageDtos {

    private PageDtos() {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    /** One row in the admin page list, summarised across languages. */
    public record PageRow(
            Long id,
            String code,
            Page.PageType pageType,
            boolean system,
            String layout,
            int sortOrder,
            /** Per-language publication state, so the list shows what is actually live. */
            List<LanguageState> languages,
            OffsetDateTime updatedAt
    ) {
    }

    public record LanguageState(
            String languageCode,
            String title,
            String slug,
            boolean published,
            boolean hasDraft,
            OffsetDateTime publishedAt
    ) {
    }

    public record PageResponse(
            Long id,
            String code,
            Page.PageType pageType,
            boolean system,
            String layout,
            int sortOrder,
            List<LanguageState> languages
    ) {
    }

    public record VersionResponse(
            Long id,
            Long pageId,
            String pageCode,
            Integer versionNumber,
            String languageCode,
            String slug,
            String title,
            String shortDescription,
            Long coverMediaId,
            String coverUrl,
            String seoTitle,
            String seoDescription,
            String seoKeywords,
            Long ogMediaId,
            String canonicalUrl,
            boolean allowIndexing,
            VersionStatus status,
            OffsetDateTime publishedAt,
            OffsetDateTime updatedAt,
            List<SectionResponse> sections
    ) {
    }

    public record SectionResponse(
            Long id,
            SectionType sectionType,
            String sectionLabel,
            String variant,
            List<String> availableVariants,
            List<String> usedFields,
            String eyebrow,
            String title,
            String subtitle,
            String content,
            Long mediaId,
            String mediaUrl,
            Long backgroundMediaId,
            String backgroundMediaUrl,
            String buttonText,
            String buttonUrl,
            String secondaryButtonText,
            String secondaryButtonUrl,
            PageSection.BackgroundStyle backgroundStyle,
            JsonNode configuration,
            int sortOrder,
            boolean active
    ) {
    }

    /** The registry, sent once so the editor can build its "add section" palette. */
    public record SectionTypeInfo(
            SectionType type,
            String label,
            String description,
            List<String> fields,
            List<String> variants
    ) {
        public static SectionTypeInfo from(SectionType type) {
            return new SectionTypeInfo(
                    type, type.label(), type.description(),
                    type.fields().stream().map(Enum::name).toList(),
                    type.variants());
        }
    }

    // --- requests -----------------------------------------------------------

    public record CreatePageRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,64}",
                    message = "Kod böyük hərf, rəqəm və alt xətdən ibarət olmalıdır")
            String code,
            @NotNull Page.PageType pageType,
            @Size(max = 32) String layout,
            Integer sortOrder,

            /** The first version is created together with the page. */
            @NotBlank @Size(max = 8) String languageCode,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 180) String slug
    ) {
    }

    public record UpdatePageRequest(
            @NotNull Page.PageType pageType,
            @Size(max = 32) String layout,
            Integer sortOrder
    ) {
    }

    public record CreateVersionRequest(
            @NotBlank @Size(max = 8) String languageCode,
            /** Copy content from this version; null starts from the published one. */
            Long sourceVersionId,
            @Size(max = 255) String title,
            @Size(max = 180) String slug
    ) {
    }

    public record UpdateVersionRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 180) String slug,
            @Size(max = 500) String shortDescription,
            Long coverMediaId,
            @Size(max = 255) String seoTitle,
            @Size(max = 500) String seoDescription,
            @Size(max = 500) String seoKeywords,
            Long ogMediaId,
            @Size(max = 500) String canonicalUrl,
            Boolean allowIndexing
    ) {
    }

    public record SaveSectionRequest(
            @NotNull SectionType sectionType,
            @Size(max = 32) String variant,
            @Size(max = 120) String eyebrow,
            @Size(max = 255) String title,
            @Size(max = 500) String subtitle,
            String content,
            Long mediaId,
            Long backgroundMediaId,
            @Size(max = 120) String buttonText,
            @Size(max = 500) String buttonUrl,
            @Size(max = 120) String secondaryButtonText,
            @Size(max = 500) String secondaryButtonUrl,
            PageSection.BackgroundStyle backgroundStyle,
            JsonNode configuration,
            Integer sortOrder,
            Boolean active
    ) {
    }

    public record ReorderRequest(@NotEmpty List<@Valid ReorderItem> items) {
    }

    public record ReorderItem(@NotNull Long id, @NotNull Integer sortOrder) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    /** What the storefront renderer consumes. Hidden sections are already filtered out. */
    public record PublicPage(
            String code,
            String slug,
            String title,
            String shortDescription,
            String coverUrl,
            String seoTitle,
            String seoDescription,
            String canonicalUrl,
            boolean allowIndexing,
            String ogUrl,
            String layout,
            List<PublicSection> sections
    ) {
    }

    public record PublicSection(
            Long id,
            SectionType type,
            String variant,
            String eyebrow,
            String title,
            String subtitle,
            String content,
            String mediaUrl,
            boolean mediaIsVideo,
            String backgroundMediaUrl,
            boolean backgroundMediaIsVideo,
            String buttonText,
            String buttonUrl,
            String secondaryButtonText,
            String secondaryButtonUrl,
            PageSection.BackgroundStyle backgroundStyle,
            JsonNode configuration
    ) {
    }
}
