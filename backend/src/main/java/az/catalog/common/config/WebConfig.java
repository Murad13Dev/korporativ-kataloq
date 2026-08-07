package az.catalog.common.config;

import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;
    private final LanguageService languageService;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        if (!"local".equalsIgnoreCase(properties.getStorage().getType())) {
            return;
        }
        Path root = Paths.get(properties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(root.toUri().toString())
                // Stored names are content-addressed, so a long TTL is safe.
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }

    @Override
    public void addArgumentResolvers(@NonNull java.util.List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestLanguageArgumentResolver(languageService));
    }
}
