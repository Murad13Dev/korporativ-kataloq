package az.catalog.modules.catalog.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Slug + last change, for the sitemap — a full entity fetch would be wasted work. */
    interface SlugAndUpdatedAt {
        String getSlug();
        OffsetDateTime getUpdatedAt();
    }

    @EntityGraph(attributePaths = {"translations", "category"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    default Optional<Product> findAliveById(Long id) {
        return findByIdAndDeletedAtIsNull(id);
    }

    /**
     * Full aggregate for the product detail page. Only {@code images} is join-fetched, and
     * nothing else that is itself a collection. Two lessons learned the hard way (see the
     * cart bug this session): Hibernate refuses outright to join-fetch two {@code List}s in
     * one query ("cannot simultaneously fetch multiple bags"), and even where it allows a
     * bag alongside a per-language {@code Map} (like {@code translations}), the join still
     * fans the SQL result out to one row per (image × translation) pair — {@code DISTINCT}
     * does NOT reliably collapse that back for {@code @EntityGraph}-driven fetches in this
     * Hibernate version, it was tried and it did not work. {@code translations} lazy-loads
     * instead; for a single product that is one cheap extra query.
     */
    @EntityGraph(attributePaths = {"category", "images", "images.media"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.slug = :slug AND p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            """)
    Optional<Product> findPublishedBySlug(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"category", "images", "images.media"})
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findDetailById(@Param("id") Long id);

    @Query("""
            SELECT COUNT(p) > 0 FROM Product p
            WHERE upper(p.sku) = upper(:sku) AND p.deletedAt IS NULL
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsBySku(@Param("sku") String sku, @Param("excludeId") Long excludeId);

    @Query("""
            SELECT COUNT(p) > 0 FROM Product p
            WHERE p.slug = :slug AND p.deletedAt IS NULL
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    // --- discount preview / campaign targeting -------------------------------

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.id IN :ids AND p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            """)
    List<Product> findAllPublishedByIds(@Param("ids") Collection<Long> ids);

    /**
     * No {@code @EntityGraph} — Hibernate rejects one on a native query outright ("A native
     * SQL query cannot use EntityGraphs"; this exact combination is what broke the discount
     * impact preview for any category-scoped campaign). {@code translations} lazy-loads
     * instead within the caller's transaction.
     */
    @Query(value = """
            SELECT p.* FROM products p
            JOIN categories pc ON pc.id = p.category_id
            WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1 FROM categories c
                  WHERE c.id IN (:categoryIds)
                    AND (pc.id = c.id OR pc.path LIKE c.path || '/%')
              )
            """, nativeQuery = true)
    List<Product> findAllPublishedInCategoryTree(@Param("categoryIds") Collection<Long> categoryIds);

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.price IS NOT NULL AND p.showPrice = true
            """)
    List<Product> findAllPublishedWithPrice();

    // --- storefront lists ----------------------------------------------------

    @EntityGraph(attributePaths = {"translations", "category", "images", "images.media"})
    @Query("""
            SELECT DISTINCT p FROM Product p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.featured = true
            ORDER BY p.sortOrder ASC, p.createdAt DESC
            """)
    List<Product> findFeatured(Pageable pageable);

    /**
     * No {@code Pageable} here, so unlike {@code findFeatured} below this cannot rely on
     * Hibernate's paginated-fetch loading strategy to sidestep the fan-out — only
     * {@code translations} is join-fetched (name resolution is needed for every result);
     * {@code images} lazy-loads instead, batched across the whole id list in one
     * {@code IN (...)} query rather than one per product.
     */
    @EntityGraph(attributePaths = {"translations", "category"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.id IN :ids
            """)
    List<Product> findPublishedCardsByIds(@Param("ids") Collection<Long> ids);

    /**
     * Curated relations first; the query falls back to same-category items in the service.
     * No {@code @EntityGraph} — Hibernate rejects one on a native query outright ("A native
     * SQL query cannot use EntityGraphs"). Associations lazy-load instead, which is cheap
     * here: the caller runs inside one transaction and the result set is capped at {@code limit}.
     */
    @Query(value = """
            SELECT p.* FROM products p
            JOIN product_relations r ON r.related_product_id = p.id
            WHERE r.product_id = :productId
              AND p.deleted_at IS NULL AND p.status = 'ACTIVE'
            ORDER BY r.sort_order
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> findRelated(@Param("productId") Long productId, @Param("limit") int limit);

    @EntityGraph(attributePaths = {"translations", "category", "images", "images.media"})
    @Query("""
            SELECT DISTINCT p FROM Product p
            WHERE p.deletedAt IS NULL
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
              AND p.category.id = :categoryId
              AND p.id <> :excludeId
            ORDER BY p.featured DESC, p.sortOrder ASC
            """)
    List<Product> findSameCategory(@Param("categoryId") Long categoryId,
                                   @Param("excludeId") Long excludeId,
                                   Pageable pageable);

    // --- counters ------------------------------------------------------------

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.deletedAt IS NULL AND p.category.id IN :categoryIds
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            """)
    long countPublishedInCategories(@Param("categoryIds") Collection<Long> categoryIds);

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.deletedAt IS NULL AND p.category.id = :categoryId
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            """)
    long countActiveInCategory(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.deletedAt IS NULL AND p.status = :status")
    long countByStatus(@Param("status") az.catalog.common.domain.ContentStatus status);

    long countByDeletedAtIsNull();

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.deletedAt IS NULL AND p.trackStock = true
              AND p.stockQuantity <= p.lowStockThreshold
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            """)
    long countLowStock();

    @EntityGraph(attributePaths = {"translations"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.deletedAt IS NULL AND p.trackStock = true
              AND p.stockQuantity <= p.lowStockThreshold
              AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY p.stockQuantity ASC
            """)
    List<Product> findLowStock(Pageable pageable);

    @EntityGraph(attributePaths = {"translations", "images", "images.media"})
    @Query("SELECT DISTINCT p FROM Product p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<Product> findRecent(Pageable pageable);

    /**
     * Atomic stock decrement. The {@code stock_quantity >= :quantity} guard makes this a
     * compare-and-set: two concurrent checkouts for the last unit cannot both succeed.
     */
    @Modifying
    @Query("""
            UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity
            WHERE p.id = :id AND (p.trackStock = false OR p.allowBackorder = true OR p.stockQuantity >= :quantity)
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL ORDER BY p.viewCount DESC")
    @EntityGraph(attributePaths = {"translations"})
    List<Product> findMostViewed(Pageable pageable);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    // --- sitemap ---------------------------------------------------------------

    @Query("""
            SELECT p.slug AS slug, p.updatedAt AS updatedAt FROM Product p
            WHERE p.deletedAt IS NULL AND p.status = az.catalog.common.domain.ContentStatus.ACTIVE
            ORDER BY p.id
            """)
    List<SlugAndUpdatedAt> findAllPublishedSlugs();
}
