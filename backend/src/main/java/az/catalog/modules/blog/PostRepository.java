package az.catalog.modules.blog;

import az.catalog.common.domain.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** Slug + last change, for the sitemap — a full entity fetch would be wasted work. */
    interface SlugAndUpdatedAt {
        String getSlug();
        OffsetDateTime getUpdatedAt();
    }

    @EntityGraph(attributePaths = {"translations", "category", "category.translations", "tags"})
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Post> findDetailById(@Param("id") Long id);

    /**
     * Public lookup. Both the status and the publish moment are checked, so a post
     * scheduled for next week is genuinely unreachable rather than merely hidden
     * from listings.
     */
    @EntityGraph(attributePaths = {"translations", "category", "category.translations", "tags",
            "tags.translations"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.slug = :slug AND p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.publishedAt IS NOT NULL AND p.publishedAt <= :now
            """)
    Optional<Post> findLiveBySlug(@Param("slug") String slug, @Param("now") OffsetDateTime now);

    @EntityGraph(attributePaths = {"translations", "category", "category.translations"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.publishedAt IS NOT NULL AND p.publishedAt <= :now
              AND (:categorySlug IS NULL OR p.category.slug = :categorySlug)
              AND (:tagSlug IS NULL OR EXISTS (
                    SELECT 1 FROM p.tags t WHERE t.slug = :tagSlug))
              AND (:search IS NULL OR EXISTS (
                    SELECT 1 FROM PostTranslation tr
                    WHERE tr.post = p AND tr.languageCode = :language
                      AND (lower(tr.title) LIKE lower(concat('%', :search, '%'))
                        OR lower(tr.shortDescription) LIKE lower(concat('%', :search, '%')))))
            """)
    Page<Post> findLive(@Param("now") OffsetDateTime now,
                        @Param("categorySlug") String categorySlug,
                        @Param("tagSlug") String tagSlug,
                        @Param("search") String search,
                        @Param("language") String language,
                        Pageable pageable);

    @EntityGraph(attributePaths = {"translations", "category", "category.translations"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.publishedAt IS NOT NULL AND p.publishedAt <= :now
              AND p.featured = true
            ORDER BY p.publishedAt DESC
            """)
    List<Post> findFeatured(@Param("now") OffsetDateTime now, Pageable pageable);

    /** Same category, excluding the post itself — the "read next" rail. */
    @EntityGraph(attributePaths = {"translations", "category", "category.translations"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.publishedAt IS NOT NULL AND p.publishedAt <= :now
              AND p.id <> :excludeId
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            ORDER BY p.publishedAt DESC
            """)
    List<Post> findRelated(@Param("now") OffsetDateTime now,
                           @Param("categoryId") Long categoryId,
                           @Param("excludeId") Long excludeId,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"translations", "category", "category.translations"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND (:status IS NULL OR p.status = :status)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:search IS NULL OR EXISTS (
                    SELECT 1 FROM PostTranslation tr
                    WHERE tr.post = p
                      AND lower(tr.title) LIKE lower(concat('%', :search, '%'))))
            """)
    Page<Post> adminSearch(@Param("search") String search,
                           @Param("status") ContentStatus status,
                           @Param("categoryId") Long categoryId,
                           Pageable pageable);

    @Query("""
            SELECT COUNT(p) > 0 FROM Post p
            WHERE p.slug = :slug AND p.deletedAt IS NULL
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL AND p.category.id = :categoryId")
    long countInCategory(@Param("categoryId") Long categoryId);

    // --- sitemap ---------------------------------------------------------------

    @Query("""
            SELECT p.slug AS slug, p.updatedAt AS updatedAt FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.publishedAt IS NOT NULL AND p.publishedAt <= :now
            ORDER BY p.id
            """)
    List<SlugAndUpdatedAt> findAllLiveSlugs(@Param("now") OffsetDateTime now);
}
