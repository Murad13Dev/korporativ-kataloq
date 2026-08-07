package az.catalog.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * In-process caches for the handful of reads that happen on literally every storefront
 * request. Everything cached here is invalidated explicitly on publish, so a short TTL
 * is only a safety net rather than the correctness mechanism.
 */
@Configuration
public class CacheConfig {

    public static final String SITE_CONFIG = "siteConfiguration";
    public static final String NAVIGATION = "navigation";
    public static final String LANGUAGES = "languages";
    public static final String PUBLIC_PAGE = "publicPage";
    public static final String ACTIVE_DISCOUNTS = "activeDiscounts";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        manager.setCacheNames(List.of(SITE_CONFIG, NAVIGATION, LANGUAGES, PUBLIC_PAGE, ACTIVE_DISCOUNTS));
        return manager;
    }
}
