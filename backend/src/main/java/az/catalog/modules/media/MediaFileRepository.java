package az.catalog.modules.media;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    Optional<MediaFile> findByChecksum(String checksum);

    @Query("""
            SELECT m FROM MediaFile m
            WHERE (:search IS NULL OR lower(m.originalName) LIKE lower(concat('%', :search, '%'))
                                   OR lower(m.altText) LIKE lower(concat('%', :search, '%')))
              AND (:mediaType IS NULL OR m.mediaType = :mediaType)
              AND (:folder IS NULL OR m.folder = :folder)
            """)
    Page<MediaFile> search(@Param("search") String search,
                           @Param("mediaType") MediaFile.MediaType mediaType,
                           @Param("folder") String folder,
                           Pageable pageable);

    @Query("SELECT DISTINCT m.folder FROM MediaFile m ORDER BY m.folder")
    List<String> findFolders();

    List<MediaFile> findAllByIdIn(Collection<Long> ids);

    /**
     * Counts every reference to a media file across the schema. A file that is in use
     * cannot be deleted — there is deliberately no force-delete endpoint, because a
     * missing image in a published page is far more expensive than a stale file on disk.
     */
    @Query(value = """
            SELECT
              (SELECT COUNT(*) FROM product_images WHERE media_id = :id)
            + (SELECT COUNT(*) FROM product_documents WHERE media_id = :id)
            + (SELECT COUNT(*) FROM categories WHERE image_media_id = :id OR icon_media_id = :id OR banner_media_id = :id)
            + (SELECT COUNT(*) FROM page_versions WHERE cover_media_id = :id OR og_media_id = :id)
            + (SELECT COUNT(*) FROM page_sections WHERE media_id = :id OR background_media_id = :id)
            + (SELECT COUNT(*) FROM posts WHERE cover_media_id = :id)
            + (SELECT COUNT(*) FROM partners WHERE logo_media_id = :id)
            + (SELECT COUNT(*) FROM testimonials WHERE photo_media_id = :id)
            + (SELECT COUNT(*) FROM benefits WHERE icon_media_id = :id)
            + (SELECT COUNT(*) FROM users WHERE avatar_media_id = :id)
            + (SELECT COUNT(*) FROM messages WHERE media_id = :id)
            + (SELECT COUNT(*) FROM site_configuration_versions
               WHERE configuration -> 'branding' @> jsonb_build_object('logoLightMediaId', :id)
                  OR configuration -> 'branding' @> jsonb_build_object('logoDarkMediaId', :id)
                  OR configuration -> 'branding' @> jsonb_build_object('mobileLogoMediaId', :id)
                  OR configuration -> 'branding' @> jsonb_build_object('faviconMediaId', :id)
                  OR configuration -> 'branding' @> jsonb_build_object('defaultOgMediaId', :id))
            """, nativeQuery = true)
    long countUsages(@Param("id") Long id);

    long count();

    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM MediaFile m")
    long totalStorageBytes();
}
