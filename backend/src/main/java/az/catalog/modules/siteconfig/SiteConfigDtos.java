package az.catalog.modules.siteconfig;

import az.catalog.common.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class SiteConfigDtos {

    private SiteConfigDtos() {
    }

    public record VersionResponse(
            Long id,
            Integer versionNumber,
            VersionStatus status,
            String label,
            JsonNode configuration,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime publishedAt
    ) {
        public static VersionResponse from(SiteConfigurationVersion version) {
            return new VersionResponse(
                    version.getId(), version.getVersionNumber(), version.getStatus(), version.getLabel(),
                    version.getConfiguration(), version.getCreatedAt(), version.getUpdatedAt(),
                    version.getPublishedAt());
        }
    }

    public record VersionSummary(
            Long id,
            Integer versionNumber,
            VersionStatus status,
            String label,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt,
            Long createdBy
    ) {
        public static VersionSummary from(SiteConfigurationVersion version) {
            return new VersionSummary(
                    version.getId(), version.getVersionNumber(), version.getStatus(), version.getLabel(),
                    version.getCreatedAt(), version.getPublishedAt(), version.getCreatedBy());
        }
    }

    /** Partial update: only the sections present in {@code configuration} are merged. */
    public record UpdateDraftRequest(
            @NotNull JsonNode configuration,
            @Size(max = 160) String label
    ) {
    }

    public record CreateDraftRequest(@Size(max = 160) String label) {
    }
}
