package az.catalog.common.config;

import az.catalog.common.security.AuthPrincipal;
import az.catalog.common.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
public class JpaConfig {

    /**
     * Fills {@code created_by} / {@code updated_by}. Only admin ids are recorded: storefront
     * writes are attributed through their own columns (customer_id on orders, sender_id on messages).
     */
    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> SecurityUtils.currentPrincipal()
                .filter(AuthPrincipal::isAdmin)
                .map(AuthPrincipal::id)
                .or(Optional::empty);
    }

    /**
     * Spring Data's built-in {@code CurrentDateTimeProvider} cannot populate an
     * {@code OffsetDateTime}-typed {@code @CreatedDate}/{@code @LastModifiedDate} field — every
     * entity in {@code BaseEntity} uses one, so this override is required, not optional.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
