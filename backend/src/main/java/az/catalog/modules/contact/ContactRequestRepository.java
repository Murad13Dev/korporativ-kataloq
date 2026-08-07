package az.catalog.modules.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {

    @Query("""
            SELECT r FROM ContactRequest r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:requestType IS NULL OR r.requestType = :requestType)
              AND (:from IS NULL OR r.createdAt >= :from)
              AND (:to IS NULL OR r.createdAt <= :to)
              AND (:search IS NULL
                   OR lower(r.name) LIKE lower(concat('%', :search, '%'))
                   OR lower(r.email) LIKE lower(concat('%', :search, '%'))
                   OR lower(r.subject) LIKE lower(concat('%', :search, '%'))
                   OR lower(r.companyName) LIKE lower(concat('%', :search, '%')))
            """)
    Page<ContactRequest> search(@Param("search") String search,
                                @Param("status") ContactRequest.Status status,
                                @Param("requestType") ContactRequest.RequestType requestType,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);

    @Query("SELECT COUNT(r) FROM ContactRequest r WHERE r.status = :status")
    long countByStatus(@Param("status") ContactRequest.Status status);

    /**
     * How many requests this address sent recently. Used to reject a flood from one
     * sender even when the IP rotates, which a per-IP limiter alone cannot catch.
     */
    @Query("""
            SELECT COUNT(r) FROM ContactRequest r
            WHERE lower(r.email) = lower(:email) AND r.createdAt >= :since
            """)
    long countRecentFromEmail(@Param("email") String email, @Param("since") OffsetDateTime since);
}
