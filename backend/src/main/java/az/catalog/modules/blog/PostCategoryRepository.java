package az.catalog.modules.blog;

import az.catalog.modules.blog.BlogTaxonomy.PostCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostCategoryRepository extends JpaRepository<PostCategory, Long> {

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT c FROM BlogTaxonomy$PostCategory c ORDER BY c.sortOrder, c.id")
    List<PostCategory> findAllOrdered();

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT c FROM BlogTaxonomy$PostCategory c
            WHERE c.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY c.sortOrder, c.id
            """)
    List<PostCategory> findActive();

    @EntityGraph(attributePaths = {"translations"})
    Optional<PostCategory> findBySlug(String slug);

    @Query("""
            SELECT COUNT(c) > 0 FROM BlogTaxonomy$PostCategory c
            WHERE c.slug = :slug AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    boolean existsBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);
}
