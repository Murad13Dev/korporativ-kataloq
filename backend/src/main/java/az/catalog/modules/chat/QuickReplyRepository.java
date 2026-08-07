package az.catalog.modules.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuickReplyRepository extends JpaRepository<QuickReply, Long> {

    @Query("""
            SELECT q FROM QuickReply q
            WHERE q.active = true
              AND (:language IS NULL OR q.languageCode IS NULL OR q.languageCode = :language)
            ORDER BY q.sortOrder, q.id
            """)
    List<QuickReply> findActive(@Param("language") String language);
}
