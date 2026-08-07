package az.catalog.common.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the resolved content language into a controller method.
 *
 * The API contract fixes {@code Accept-Language} as the single source of truth — query
 * parameters and headers are never mixed. Unknown or inactive codes fall back to the
 * site default rather than producing an empty response.
 *
 * <pre>{@code
 * @GetMapping("/products")
 * public PageResponse<ProductCard> list(@RequestLanguage String language) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestLanguage {
}
