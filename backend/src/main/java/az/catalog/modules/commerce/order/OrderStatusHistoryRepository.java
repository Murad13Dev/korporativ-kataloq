package az.catalog.modules.commerce.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findAllByOrderIdOrderByCreatedAtAsc(Long orderId);
}
