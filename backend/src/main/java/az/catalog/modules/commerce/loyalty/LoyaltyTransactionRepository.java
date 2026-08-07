package az.catalog.modules.commerce.loyalty;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    Page<LoyaltyTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    boolean existsByOrderIdAndType(Long orderId, LoyaltyTransaction.Type type);

    @Query("SELECT COALESCE(SUM(t.points), 0) FROM LoyaltyTransaction t WHERE t.customer.id = :customerId")
    int balanceOf(@Param("customerId") Long customerId);
}
