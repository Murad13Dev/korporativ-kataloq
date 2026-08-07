package az.catalog.modules.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findAllByConversationIdOrderByCreatedAtAsc(Long conversationId);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId AND m.id > :afterId
            ORDER BY m.createdAt ASC
            """)
    List<Message> findSince(@Param("conversationId") Long conversationId,
                            @Param("afterId") Long afterId);

    /**
     * Marks the other party's messages as read. The sender-type filter is what stops
     * an operator's own messages from being counted against them.
     */
    @Modifying
    @Query("""
            UPDATE Message m SET m.read = true, m.readAt = :now
            WHERE m.conversationId = :conversationId
              AND m.senderType = :senderType
              AND m.read = false
            """)
    int markRead(@Param("conversationId") Long conversationId,
                 @Param("senderType") Message.SenderType senderType,
                 @Param("now") OffsetDateTime now);

    long countByConversationId(Long conversationId);
}
