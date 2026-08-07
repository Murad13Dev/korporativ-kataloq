package az.catalog.modules.commerce.promo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {

    @Query("""
            SELECT COUNT(r) FROM PromoCodeRedemption r
            WHERE r.promoCodeId = :promoCodeId
              AND (( :customerId IS NOT NULL AND r.customerId = :customerId)
                OR ( :customerId IS NULL AND lower(r.customerEmail) = lower(:email)))
            """)
    long countForCustomer(@Param("promoCodeId") Long promoCodeId,
                          @Param("customerId") Long customerId,
                          @Param("email") String email);

    void deleteByOrderId(Long orderId);
}
