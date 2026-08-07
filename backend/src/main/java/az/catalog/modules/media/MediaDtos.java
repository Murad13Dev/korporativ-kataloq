package az.catalog.modules.media;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class MediaDtos {

    private MediaDtos() {
    }

    public record MediaResponse(
            Long id,
            String originalName,
            String publicUrl,
            String mimeType,
            long fileSize,
            MediaFile.MediaType mediaType,
            Integer width,
            Integer height,
            String altText,
            String title,
            String folder,
            OffsetDateTime createdAt
    ) {
        public static MediaResponse from(MediaFile media) {
            return new MediaResponse(
                    media.getId(), media.getOriginalName(), media.getPublicUrl(), media.getMimeType(),
                    media.getFileSize(), media.getMediaType(), media.getWidth(), media.getHeight(),
                    media.getAltText(), media.getTitle(), media.getFolder(), media.getCreatedAt());
        }
    }

    public record UpdateRequest(
            @Size(max = 255) String altText,
            @Size(max = 255) String title,
            @Size(max = 120) String folder
    ) {
    }

    public record StorageStats(long fileCount, long totalBytes) {
    }

    public record UsageResponse(long usageCount, boolean deletable) {
    }
}
