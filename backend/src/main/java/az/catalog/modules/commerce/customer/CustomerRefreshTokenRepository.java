package az.catalog.modules.commerce.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CustomerRefreshTokenRepository extends JpaRepository<CustomerRefreshToken, Long> {

    Optional<CustomerRefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE CustomerRefreshToken t SET t.revokedAt = :now
            WHERE t.customerId = :customerId AND t.revokedAt IS NULL
            """)
    int revokeAllForCustomer(@Param("customerId") Long customerId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM CustomerRefreshToken t WHERE t.expiresAt < :cutoff OR t.revokedAt < :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
