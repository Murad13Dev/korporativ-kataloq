package az.catalog.modules.blog;

import az.catalog.modules.blog.BlogTaxonomy.Tag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT t FROM BlogTaxonomy$Tag t ORDER BY t.code")
    List<Tag> findAllOrdered();

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT t FROM BlogTaxonomy$Tag t WHERE t.id IN :ids")
    List<Tag> findAllByIds(@Param("ids") Collection<Long> ids);

    Optional<Tag> findByCode(String code);

    /** Only tags at least one live post carries — an empty tag cloud entry is a dead end. */
    @Query(value = """
            SELECT DISTINCT t.* FROM tags t
            JOIN post_tags pt ON pt.tag_id = t.id
            JOIN posts p ON p.id = pt.post_id
            WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND p.published_at IS NOT NULL AND p.published_at <= now()
            ORDER BY t.code
            """, nativeQuery = true)
    List<Tag> findUsedTags();
}
