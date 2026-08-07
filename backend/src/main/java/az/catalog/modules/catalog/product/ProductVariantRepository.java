package az.catalog.modules.catalog.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * Only {@code options} is join-fetched — the single collection an EntityGraph here
     * can safely carry, per the fan-out lesson learned elsewhere in this codebase.
     * {@code options.attribute}/{@code options.translations} lazy-load, batched.
     */
    @EntityGraph(attributePaths = {"options"})
    List<ProductVariant> findAllByProductIdOrderBySortOrderAscIdAsc(Long productId);

    @EntityGraph(attributePaths = {"options"})
    Optional<ProductVariant> findByIdAndProductId(Long id, Long productId);

    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsBySku(String sku);

    long countByProductId(Long productId);

    long countByProductIdAndActiveTrue(Long productId);

    /** Mirrors {@code ProductRepository.decrementStock} exactly — same compare-and-set guard. */
    @Modifying
    @Query("""
            UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity - :quantity
            WHERE v.id = :id AND (v.trackStock = false OR v.allowBackorder = true OR v.stockQuantity >= :quantity)
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity + :quantity WHERE v.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
