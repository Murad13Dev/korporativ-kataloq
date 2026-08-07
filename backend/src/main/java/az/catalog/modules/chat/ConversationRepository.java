package az.catalog.modules.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * The visitor's own open thread. Scoped by token, which is what makes this safe
     * to call from an unauthenticated endpoint.
     */
    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.visitorToken = :token
              AND c.status <> az.catalog.modules.chat.Conversation$Status.CLOSED
            ORDER BY c.createdAt DESC
            """)
    List<Conversation> findOpenByVisitorToken(@Param("token") String token);

    @EntityGraph(attributePaths = {"customer"})
    Optional<Conversation> findByIdAndVisitorToken(Long id, String visitorToken);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findDetailById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            SELECT c FROM Conversation c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:assignedAdminId IS NULL OR c.assignedAdminId = :assignedAdminId)
              AND (:unreadOnly = false OR c.unreadByAdmin > 0)
              AND (:search IS NULL
                   OR lower(c.visitorName) LIKE lower(concat('%', :search, '%'))
                   OR lower(c.visitorEmail) LIKE lower(concat('%', :search, '%'))
                   OR lower(c.subject) LIKE lower(concat('%', :search, '%')))
            """)
    Page<Conversation> inbox(@Param("search") String search,
                             @Param("status") Conversation.Status status,
                             @Param("assignedAdminId") Long assignedAdminId,
                             @Param("unreadOnly") boolean unreadOnly,
                             Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT c FROM Conversation c WHERE c.customer.id = :customerId ORDER BY c.createdAt DESC")
    List<Conversation> findByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(c.unreadByAdmin), 0) FROM Conversation c WHERE c.unreadByAdmin > 0")
    long totalUnreadForAdmin();

    @Query("""
            SELECT COUNT(c) FROM Conversation c
            WHERE c.status = az.catalog.modules.chat.Conversation$Status.OPEN
            """)
    long countOpen();

    /** Threads idle past the configured window, closed by the housekeeping job. */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.status <> az.catalog.modules.chat.Conversation$Status.CLOSED
              AND c.lastMessageAt IS NOT NULL AND c.lastMessageAt < :cutoff
            """)
    List<Conversation> findStale(@Param("cutoff") OffsetDateTime cutoff);
}
