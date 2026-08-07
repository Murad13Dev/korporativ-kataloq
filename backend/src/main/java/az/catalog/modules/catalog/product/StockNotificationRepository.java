package az.catalog.modules.catalog.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockNotificationRepository extends JpaRepository<StockNotification, Long> {

    boolean existsByProductIdAndEmailIgnoreCase(Long productId, String email);

    List<StockNotification> findByProductIdAndNotifiedAtIsNull(Long productId);
}
