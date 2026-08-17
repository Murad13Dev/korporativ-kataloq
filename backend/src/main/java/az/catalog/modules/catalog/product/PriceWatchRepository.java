package az.catalog.modules.catalog.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, Long> {

    boolean existsByProductIdAndEmailIgnoreCase(Long productId, String email);

    List<PriceWatch> findByProductIdInAndNotifiedAtIsNull(List<Long> productIds);
}
