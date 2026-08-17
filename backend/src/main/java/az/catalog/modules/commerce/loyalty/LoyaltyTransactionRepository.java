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

    /** Lifetime earned, ignoring redemptions — a tier reflects how much a customer has
        spent over time, not their current redeemable balance. Basing it on the net
        balance instead would demote someone the moment they redeem points, which is
        the "spend and lose your status" problem real loyalty programs avoid. */
    @Query("""
            SELECT COALESCE(SUM(t.points), 0) FROM LoyaltyTransaction t
            WHERE t.customer.id = :customerId AND t.type = az.catalog.modules.commerce.loyalty.LoyaltyTransaction.Type.EARNED
            """)
    int lifetimeEarned(@Param("customerId") Long customerId);
}
