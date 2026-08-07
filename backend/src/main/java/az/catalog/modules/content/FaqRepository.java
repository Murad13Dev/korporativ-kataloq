package az.catalog.modules.content;

import az.catalog.modules.content.ContentEntities.Faq;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    /**
     * @param pageId when non-null, returns that page's entries plus the global ones,
     *               so a contact page shows both shipping FAQs and general ones
     */
    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT f FROM ContentEntities$Faq f
            WHERE f.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND (:pageId IS NULL OR f.pageId IS NULL OR f.pageId = :pageId)
              AND (:category IS NULL OR f.category = :category)
            ORDER BY f.sortOrder, f.id
            """)
    List<Faq> findActive(@Param("pageId") Long pageId, @Param("category") String category);

    @EntityGraph(attributePaths = {"translations"})
    @Query("SELECT f FROM ContentEntities$Faq f ORDER BY f.sortOrder, f.id")
    List<Faq> findAllOrdered();
}
