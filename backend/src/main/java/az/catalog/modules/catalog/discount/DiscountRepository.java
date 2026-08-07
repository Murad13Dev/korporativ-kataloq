package az.catalog.modules.catalog.discount;

import az.catalog.common.domain.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    /**
     * Every campaign that could apply right now. Deliberately small in practice (a store
     * runs a handful of campaigns at once), which is what makes caching the whole set and
     * matching in memory cheaper than a per-product join.
     */
    @EntityGraph(attributePaths = {"productIds", "categoryIds"})
    @Query("""
            SELECT d FROM Discount d
            WHERE d.deletedAt IS NULL
              AND d.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND (d.startsAt IS NULL OR d.startsAt <= :now)
              AND (d.endsAt IS NULL OR d.endsAt > :now)
            ORDER BY d.priority DESC, d.id ASC
            """)
    List<Discount> findRunning(@Param("now") OffsetDateTime now);

    @EntityGraph(attributePaths = {"productIds", "categoryIds"})
    Optional<Discount> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT d FROM Discount d
            WHERE d.deletedAt IS NULL
              AND (:search IS NULL OR lower(d.name) LIKE lower(concat('%', :search, '%')))
              AND (:status IS NULL OR d.status = :status)
              AND (:type IS NULL OR d.discountType = :type)
              AND (:scope IS NULL OR d.scope = :scope)
            """)
    Page<Discount> search(@Param("search") String search,
                          @Param("status") ContentStatus status,
                          @Param("type") DiscountType type,
                          @Param("scope") DiscountScope scope,
                          Pageable pageable);

    /** Ids of every product currently on campaign — powers the "discounted" catalog filter. */
    @Query(value = """
            SELECT DISTINCT p.id
            FROM products p
            WHERE p.deleted_at IS NULL
              AND p.status = 'ACTIVE'
              AND p.price IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM discounts d
                WHERE d.deleted_at IS NULL
                  AND d.status = 'ACTIVE'
                  AND (d.starts_at IS NULL OR d.starts_at <= :now)
                  AND (d.ends_at IS NULL OR d.ends_at > :now)
                  AND (
                       d.scope = 'ALL_PRODUCTS'
                    OR (d.scope = 'PRODUCTS' AND EXISTS (
                          SELECT 1 FROM discount_products dp
                          WHERE dp.discount_id = d.id AND dp.product_id = p.id))
                    OR (d.scope = 'CATEGORIES' AND EXISTS (
                          SELECT 1 FROM discount_categories dc
                          JOIN categories c ON c.id = dc.category_id
                          JOIN categories pc ON pc.id = p.category_id
                          WHERE dc.discount_id = d.id
                            AND (pc.id = c.id
                                 OR (dc.include_descendants AND pc.path LIKE c.path || '/%'))))
                  )
              )
            """, nativeQuery = true)
    List<Long> findDiscountedProductIds(@Param("now") OffsetDateTime now);

    @Query("""
            SELECT COUNT(d) FROM Discount d
            WHERE d.deletedAt IS NULL
              AND d.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND (d.startsAt IS NULL OR d.startsAt <= :now)
              AND (d.endsAt IS NULL OR d.endsAt > :now)
            """)
    long countRunning(@Param("now") OffsetDateTime now);
}
