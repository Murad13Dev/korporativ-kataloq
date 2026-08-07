package az.catalog.modules.catalog.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {

    /** Only {@code items} is join-fetched — the single collection an EntityGraph can safely carry. */
    @EntityGraph(attributePaths = {"items"})
    Optional<ProductBundle> findFirstByPrimaryProductIdAndActiveTrueOrderBySortOrderAsc(Long primaryProductId);

    @EntityGraph(attributePaths = {"items"})
    Optional<ProductBundle> findByIdAndPrimaryProductId(Long id, Long primaryProductId);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT b FROM ProductBundle b WHERE :search IS NULL OR lower(b.name) LIKE lower(concat('%', :search, '%'))")
    Page<ProductBundle> search(@Param("search") String search, Pageable pageable);
}
