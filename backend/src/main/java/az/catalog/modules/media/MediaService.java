package az.catalog.modules.media;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.security.SecurityUtils;
import az.catalog.common.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Upload, catalogue and delete media.
 *
 * Uploads are validated on three axes — declared MIME type, extension and magic bytes —
 * because any one of them alone is trivially spoofed. SVG uploads are additionally
 * screened for scripting, since an SVG served from our own origin is an XSS vector.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Map<String, String> EXTENSION_BY_MIME = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/avif", "avif"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"));

    /** Leading bytes that must match the declared type. */
    private static final Map<String, byte[][]> MAGIC_BYTES = Map.of(
            "image/jpeg", new byte[][]{{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}},
            "image/png", new byte[][]{{(byte) 0x89, 'P', 'N', 'G'}},
            "image/gif", new byte[][]{{'G', 'I', 'F', '8'}},
            "image/webp", new byte[][]{{'R', 'I', 'F', 'F'}},
            "application/pdf", new byte[][]{{'%', 'P', 'D', 'F'}});

    private static final Set<String> DANGEROUS_SVG_TOKENS = Set.of(
            "<script", "javascript:", "onload=", "onerror=", "onclick=", "<foreignobject", "<iframe", "<embed");

    private final MediaFileRepository repository;
    private final FileStorage storage;
    private final AppProperties properties;

    // -----------------------------------------------------------------------
    // Upload
    // -----------------------------------------------------------------------

    @Transactional
    public MediaDtos.MediaResponse upload(MultipartFile file, String folder, String altText, String title) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fayl boşdur");
        }

        byte[] bytes = readBytes(file);
        String mimeType = normaliseMimeType(file);
        MediaFile.MediaType mediaType = classify(mimeType);

        validateType(mimeType, mediaType);
        validateSize(bytes.length, mediaType);
        validateContent(bytes, mimeType);

        String checksum = sha256(bytes);
        // An identical file already in the library is reused rather than duplicated.
        var existing = repository.findByChecksum(checksum);
        if (existing.isPresent()) {
            log.debug("Reusing existing media {} for identical upload", existing.get().getId());
            return MediaDtos.MediaResponse.from(existing.get());
        }

        String safeFolder = sanitizeFolder(folder);
        String extension = EXTENSION_BY_MIME.getOrDefault(mimeType, "bin");
        String storedName = UUID.randomUUID() + "." + extension;

        String storagePath;
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            storagePath = storage.store(safeFolder, storedName, stream, bytes.length);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR);
        }

        int[] dimensions = mediaType == MediaFile.MediaType.IMAGE ? readDimensions(bytes) : null;

        MediaFile media = repository.save(MediaFile.builder()
                .originalName(truncate(file.getOriginalFilename(), 255))
                .storedName(storedName)
                .storagePath(storagePath)
                .publicUrl(storage.publicUrl(storagePath))
                .mimeType(mimeType)
                .fileSize(bytes.length)
                .mediaType(mediaType)
                .width(dimensions == null ? null : dimensions[0])
                .height(dimensions == null ? null : dimensions[1])
                .altText(truncate(altText, 255))
                .title(truncate(title, 255))
                .folder(safeFolder)
                .checksum(checksum)
                .uploadedBy(SecurityUtils.currentAdminId().orElse(null))
                .build());

        log.info("Uploaded media {} ({} bytes, {})", media.getId(), media.getFileSize(), mimeType);
        return MediaDtos.MediaResponse.from(media);
    }

    // -----------------------------------------------------------------------
    // Library
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<MediaDtos.MediaResponse> search(String search, MediaFile.MediaType type,
                                                String folder, Pageable pageable) {
        return repository.search(blankToNull(search), type, blankToNull(folder), pageable)
                .map(MediaDtos.MediaResponse::from);
    }

    @Transactional(readOnly = true)
    public MediaDtos.MediaResponse get(Long id) {
        return MediaDtos.MediaResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public List<String> folders() {
        return repository.findFolders();
    }

    @Transactional(readOnly = true)
    public MediaDtos.StorageStats stats() {
        return new MediaDtos.StorageStats(repository.count(), repository.totalStorageBytes());
    }

    @Transactional
    public MediaDtos.MediaResponse updateMetadata(Long id, MediaDtos.UpdateRequest request) {
        MediaFile media = load(id);
        media.setAltText(request.altText());
        media.setTitle(request.title());
        if (request.folder() != null && !request.folder().isBlank()) {
            media.setFolder(sanitizeFolder(request.folder()));
        }
        return MediaDtos.MediaResponse.from(media);
    }

    /**
     * Removes a file that nothing references. There is no force-delete variant: a broken
     * image on a live page costs more than an orphaned file on disk.
     */
    @Transactional
    public void delete(Long id) {
        MediaFile media = load(id);
        long usages = repository.countUsages(id);
        if (usages > 0) {
            throw new ApiException(ErrorCode.MEDIA_FILE_IN_USE,
                    "Fayl %d yerdə istifadə olunur və silinə bilməz".formatted(usages));
        }
        String path = media.getStoragePath();
        repository.delete(media);
        // Only touch the disk once the row is gone, so a failed delete cannot orphan a row.
        storage.delete(path);
        log.info("Deleted media {}", id);
    }

    @Transactional(readOnly = true)
    public long usageCount(Long id) {
        return repository.countUsages(id);
    }

    // -----------------------------------------------------------------------
    // Helpers used by other modules
    // -----------------------------------------------------------------------

    /** {@code null}-safe URL lookup for a single media id. */
    @Transactional(readOnly = true)
    public String publicUrl(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        return repository.findById(mediaId).map(MediaFile::getPublicUrl).orElse(null);
    }

    /** Batch variant — use this instead of calling {@link #publicUrl(Long)} in a loop. */
    @Transactional(readOnly = true)
    public Map<Long, String> publicUrls(java.util.Collection<Long> mediaIds) {
        List<Long> ids = mediaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            // Not Map.of(): callers look up this map by a nullable media id (most sections
            // and posts carry none), and Map.of()'s get(null) throws instead of returning null.
            return new java.util.HashMap<>();
        }
        return repository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(MediaFile::getId, MediaFile::getPublicUrl));
    }

    /** Batch "is this a video?" lookup — a section renders a `<video>` instead of an `<img>`. */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> videoFlags(java.util.Collection<Long> mediaIds) {
        List<Long> ids = mediaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new java.util.HashMap<>();
        }
        return repository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(MediaFile::getId, media -> media.getMediaType() == MediaFile.MediaType.VIDEO));
    }

    @Transactional(readOnly = true)
    public MediaFile require(Long id) {
        return load(id);
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private void validateType(String mimeType, MediaFile.MediaType mediaType) {
        List<String> allowed = switch (mediaType) {
            case IMAGE -> properties.getStorage().getAllowedImageTypes();
            case VIDEO -> properties.getStorage().getAllowedVideoTypes();
            default -> properties.getStorage().getAllowedDocumentTypes();
        };

        if (!allowed.contains(mimeType)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Bu fayl tipi dəstəklənmir: " + mimeType);
        }
    }

    private void validateSize(long size, MediaFile.MediaType mediaType) {
        long max = switch (mediaType) {
            case IMAGE -> properties.getStorage().getMaxImageSizeBytes();
            case VIDEO -> properties.getStorage().getMaxVideoSizeBytes();
            default -> properties.getStorage().getMaxDocumentSizeBytes();
        };
        if (size > max) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "Fayl həcmi %d MB-dan böyük ola bilməz".formatted(max / (1024 * 1024)));
        }
    }

    /** Magic-byte check plus SVG script screening. */
    private void validateContent(byte[] bytes, String mimeType) {
        byte[][] signatures = MAGIC_BYTES.get(mimeType);
        if (signatures != null && !matchesAnySignature(bytes, signatures)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Faylın məzmunu göstərilən tipə uyğun deyil");
        }
        if ("image/svg+xml".equals(mimeType)) {
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            for (String token : DANGEROUS_SVG_TOKENS) {
                if (content.contains(token)) {
                    throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                            "SVG faylında icazə verilməyən element var");
                }
            }
        }
    }

    private boolean matchesAnySignature(byte[] bytes, byte[][] signatures) {
        for (byte[] signature : signatures) {
            if (bytes.length >= signature.length) {
                boolean match = true;
                for (int i = 0; i < signature.length; i++) {
                    if (bytes[i] != signature[i]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normaliseMimeType(MultipartFile file) {
        String declared = file.getContentType();
        if (declared == null || declared.isBlank() || "application/octet-stream".equals(declared)) {
            // Fall back to the extension when the browser sends nothing useful.
            String name = file.getOriginalFilename();
            String extension = name != null && name.contains(".")
                    ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                    : "";
            return EXTENSION_BY_MIME.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(extension))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("application/octet-stream");
        }
        return declared.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private MediaFile.MediaType classify(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return MediaFile.MediaType.IMAGE;
        }
        if (mimeType.startsWith("video/")) {
            return MediaFile.MediaType.VIDEO;
        }
        if (mimeType.startsWith("application/") || mimeType.startsWith("text/")) {
            return MediaFile.MediaType.DOCUMENT;
        }
        return MediaFile.MediaType.OTHER;
    }

    private int[] readDimensions(byte[] bytes) {
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(stream);
            return image == null ? null : new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException | RuntimeException ex) {
            // SVG and AVIF are not readable by ImageIO; dimensions stay null.
            return null;
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR, "Fayl oxuna bilmədi");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "general";
        }
        String slug = SlugUtil.slugify(folder);
        return slug.isEmpty() ? "general" : slug;
    }

    private MediaFile load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Media faylı", id));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
