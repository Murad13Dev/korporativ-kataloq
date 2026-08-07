package az.catalog.modules.commerce.quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {

    @EntityGraph(attributePaths = {"items"})
    List<QuoteRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"items"})
    Optional<QuoteRequest> findByIdAndCustomerId(Long id, Long customerId);

    @Query("""
            SELECT q FROM QuoteRequest q
            WHERE (:status IS NULL OR q.status = :status)
              AND (:search IS NULL
                   OR lower(q.contactName) LIKE lower(concat('%', :search, '%'))
                   OR lower(q.contactEmail) LIKE lower(concat('%', :search, '%')))
            """)
    Page<QuoteRequest> search(@Param("status") QuoteStatus status, @Param("search") String search,
                              Pageable pageable);

    long countByStatus(QuoteStatus status);
}
