package az.catalog.modules.media;

import az.catalog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile extends BaseEntity {

    public enum MediaType {
        IMAGE, DOCUMENT, VIDEO, OTHER
    }

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** Randomised, content-addressed name on disk. Never derived from user input. */
    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "public_url", nullable = false, length = 700)
    private String publicUrl;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 24)
    @Builder.Default
    private MediaType mediaType = MediaType.IMAGE;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "folder", nullable = false, length = 120)
    @Builder.Default
    private String folder = "general";

    /** SHA-256 of the bytes; lets an identical re-upload reuse the existing row. */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "uploaded_by")
    private Long uploadedBy;
}
