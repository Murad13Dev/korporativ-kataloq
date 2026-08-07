package az.catalog.modules.commerce.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CustomerPasswordResetTokenRepository extends JpaRepository<CustomerPasswordResetToken, Long> {

    Optional<CustomerPasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE CustomerPasswordResetToken t SET t.usedAt = :now
            WHERE t.customerId = :customerId AND t.usedAt IS NULL
            """)
    int invalidateAllForCustomer(@Param("customerId") Long customerId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM CustomerPasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
