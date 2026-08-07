package az.catalog.modules.content;

import az.catalog.modules.content.ContentEntities.Faq;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    /**
     * Best-matching FAQ answer for a free-text message. Uses whole-string {@code similarity()}
     * rather than the {@code word_similarity()} the product search fallback uses — that
     * function is tuned for a short query against a long document (find the best-matching
     * substring), which is the wrong shape here: a customer's question and an FAQ's question
     * are both full sentences of comparable length, and word_similarity's substring-seeking
     * measurably mis-ranked a warranty FAQ above the correct delivery-time one in testing.
     * Empty when nothing clears the threshold — a wrong "helpful" guess is worse than
     * staying quiet.
     */
    @Query(value = """
            SELECT ft.answer
            FROM faq_translations ft
            JOIN faqs f ON f.id = ft.faq_id
            WHERE f.status = 'ACTIVE' AND ft.language_code = :language
              AND similarity(:message, ft.question) > :threshold
            ORDER BY similarity(:message, ft.question) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findBestMatchingAnswer(@Param("message") String message,
                                            @Param("language") String language,
                                            @Param("threshold") double threshold);
}
