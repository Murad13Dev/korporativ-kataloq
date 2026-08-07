package az.catalog.modules.catalog.product;

import az.catalog.common.domain.ContentStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the catalog list can be narrowed by. Mirrors the query string one-for-one
 * so a filtered view is always a shareable URL.
 *
 * @param attributeOptionIds SELECT / MULTI_SELECT facets; a product must match at least
 *                           one option per distinct attribute (OR within, AND across)
 */
public record ProductFilter(
        String search,
        Long categoryId,
        String categorySlug,
        Boolean featured,
        Boolean isNew,
        /** Only products currently on a discount campaign. */
        Boolean discounted,
        Boolean inStock,
        /** Stock-tracked, active, and at or below its own low-stock threshold. Admin only. */
        Boolean lowStock,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<Long> attributeOptionIds,
        List<Long> excludeIds,
        ContentStatus status
) {

    public static ProductFilter publicDefaults() {
        return new ProductFilter(null, null, null, null, null, null, null, null,
                null, null, null, null, ContentStatus.ACTIVE);
    }

    public boolean hasSearch() {
        return search != null && !search.isBlank();
    }

    /** Same filter with the search term swapped out — used for the trigram fallback pass. */
    public ProductFilter withSearch(String newSearch) {
        return new ProductFilter(newSearch, categoryId, categorySlug, featured, isNew, discounted,
                inStock, lowStock, minPrice, maxPrice, attributeOptionIds, excludeIds, status);
    }
}
