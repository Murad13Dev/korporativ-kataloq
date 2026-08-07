package az.catalog.modules.commerce.promo;

import az.catalog.common.domain.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    @Query("SELECT p FROM PromoCode p WHERE upper(p.code) = upper(:code) AND p.deletedAt IS NULL")
    Optional<PromoCode> findAliveByCode(@Param("code") String code);

    @Query("SELECT p FROM PromoCode p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<PromoCode> findAliveById(@Param("id") Long id);

    @Query("""
            SELECT COUNT(p) > 0 FROM PromoCode p
            WHERE upper(p.code) = upper(:code) AND p.deletedAt IS NULL
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Query("""
            SELECT p FROM PromoCode p
            WHERE p.deletedAt IS NULL
              AND (:search IS NULL OR lower(p.code) LIKE lower(concat('%', :search, '%')))
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<PromoCode> search(@Param("search") String search,
                           @Param("status") ContentStatus status,
                           Pageable pageable);

    /**
     * Atomic increment guarded by the usage limit. Returning 0 means the last voucher was
     * taken by a concurrent checkout, which the caller surfaces as PROMO_CODE_USAGE_LIMIT.
     */
    @Modifying
    @Query("""
            UPDATE PromoCode p SET p.usedCount = p.usedCount + 1
            WHERE p.id = :id AND (p.usageLimit IS NULL OR p.usedCount < p.usageLimit)
            """)
    int incrementUsage(@Param("id") Long id);

    @Modifying
    @Query("UPDATE PromoCode p SET p.usedCount = p.usedCount - 1 WHERE p.id = :id AND p.usedCount > 0")
    int decrementUsage(@Param("id") Long id);
}
