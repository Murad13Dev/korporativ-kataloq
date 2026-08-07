package az.catalog.modules.catalog.category;

import az.catalog.common.domain.ContentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @EntityGraph(attributePaths = {"translations"})
    Optional<Category> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"translations", "parent"})
    @Query("""
            SELECT c FROM Category c
            WHERE c.slug = :slug AND c.deletedAt IS NULL
              AND c.status = az.catalog.common.domain.ContentStatus.ACTIVE
            """)
    Optional<Category> findPublishedBySlug(@Param("slug") String slug);

    /** Whole tree in one query; assembled into a hierarchy in the service. */
    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT c FROM Category c WHERE c.deletedAt IS NULL ORDER BY c.depth ASC, c.sortOrder ASC, c.id ASC")
    List<Category> findAllAlive();

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT c FROM Category c
            WHERE c.deletedAt IS NULL AND c.status = :status
            ORDER BY c.depth ASC, c.sortOrder ASC, c.id ASC
            """)
    List<Category> findAllByStatus(@Param("status") ContentStatus status);

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT c FROM Category c
            WHERE c.deletedAt IS NULL
              AND c.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND c.featured = true
            ORDER BY c.sortOrder ASC
            """)
    List<Category> findFeatured(org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(c) > 0 FROM Category c
            WHERE c.slug = :slug AND c.deletedAt IS NULL
              AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    boolean existsBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.parent.id = :parentId AND c.deletedAt IS NULL")
    long countChildren(@Param("parentId") Long parentId);

    /** The category itself plus every descendant, via the materialised path. */
    @Query(value = """
            SELECT c.id FROM categories c
            WHERE c.deleted_at IS NULL
              AND (c.id = :categoryId OR c.path LIKE (SELECT p.path FROM categories p WHERE p.id = :categoryId) || '/%')
            """, nativeQuery = true)
    List<Long> findSubtreeIds(@Param("categoryId") Long categoryId);

    @Query("SELECT c FROM Category c WHERE c.deletedAt IS NULL AND c.parent.id = :parentId ORDER BY c.sortOrder")
    List<Category> findChildren(@Param("parentId") Long parentId);

    @Query(value = """
            SELECT * FROM categories
            WHERE deleted_at IS NULL AND (id = :rootId OR path LIKE :pathPrefix || '/%')
            """, nativeQuery = true)
    List<Category> findSubtree(@Param("rootId") Long rootId, @Param("pathPrefix") String pathPrefix);

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT c FROM Category c WHERE c.id IN :ids AND c.deletedAt IS NULL")
    List<Category> findAllAliveByIds(@Param("ids") Collection<Long> ids);

    long countByDeletedAtIsNull();
}
