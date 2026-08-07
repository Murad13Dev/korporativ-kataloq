package az.catalog.modules.catalog.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Page<ProductReview> findByProductIdAndStatusOrderByCreatedAtDesc(
            Long productId, ReviewStatus status, Pageable pageable);

    boolean existsByProductIdAndCustomerId(Long productId, Long customerId);

    /**
     * Always exactly one row (aggregates over zero matching reviews still return one row
     * of zeros) — but Spring Data JPA only unwraps a multi-column JPQL projection into a
     * bare {@code Object[]} correctly when the method is declared to return the list of
     * rows; declaring the element type directly makes it wrap that single row in an outer
     * array instead, so every access past index 0 throws.
     */
    @Query("""
            SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM ProductReview r
            WHERE r.product.id = :productId AND r.status = az.catalog.modules.catalog.review.ReviewStatus.APPROVED
            """)
    List<Object[]> ratingSummary(@Param("productId") Long productId);

    @Query("""
            SELECT r.rating, COUNT(r) FROM ProductReview r
            WHERE r.product.id = :productId AND r.status = az.catalog.modules.catalog.review.ReviewStatus.APPROVED
            GROUP BY r.rating
            """)
    List<Object[]> ratingBreakdown(@Param("productId") Long productId);

    @Query("""
            SELECT r FROM ProductReview r
            WHERE (:productId IS NULL OR r.product.id = :productId)
              AND (:status IS NULL OR r.status = :status)
              AND (:search IS NULL
                   OR lower(r.authorName) LIKE lower(concat('%', :search, '%'))
                   OR lower(r.title) LIKE lower(concat('%', :search, '%'))
                   OR lower(r.body) LIKE lower(concat('%', :search, '%')))
            """)
    Page<ProductReview> search(@Param("productId") Long productId, @Param("status") ReviewStatus status,
                               @Param("search") String search, Pageable pageable);

    long countByStatus(ReviewStatus status);
}
