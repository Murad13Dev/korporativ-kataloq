package az.catalog.modules.commerce.customer;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    /**
     * Only {@code product} and {@code product.category} are join-fetched — both
     * {@code ManyToOne}, so there's no collection-fan-out risk. Product name/image resolve
     * separately through {@code ProductService.cardsFor}, which already batches those.
     */
    @EntityGraph(attributePaths = {"product", "product.category"})
    List<WishlistItem> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<WishlistItem> findByCustomerIdAndProductId(Long customerId, Long productId);

    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    long countByCustomerId(Long customerId);
}
