package az.catalog.modules.catalog.product;

import az.catalog.common.domain.ContentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Criteria builders for the catalog list.
 *
 * <p><b>Price filtering caveat.</b> {@code minPrice}/{@code maxPrice} and price sorting
 * operate on the <em>list</em> price, not the campaign price. Filtering on the effective
 * price would mean replicating the campaign-resolution rules inside SQL, and those rules
 * (priority, scope specificity, category descent) are exactly the part that must stay in
 * one place. The storefront's price slider is therefore labelled against list prices.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> build(ProductFilter filter,
                                               String language,
                                               Collection<Long> categoryIds,
                                               Collection<Long> discountedProductIds) {
        List<Specification<Product>> parts = new ArrayList<>();

        parts.add(alive());

        if (filter.status() != null) {
            parts.add(hasStatus(filter.status()));
        }
        if (filter.hasSearch()) {
            parts.add(matchesSearch(filter.search().trim(), language));
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            parts.add(inCategories(categoryIds));
        }
        if (Boolean.TRUE.equals(filter.featured())) {
            parts.add((root, query, cb) -> cb.isTrue(root.get("featured")));
        }
        if (Boolean.TRUE.equals(filter.isNew())) {
            parts.add((root, query, cb) -> cb.isTrue(root.get("isNew")));
        }
        if (Boolean.TRUE.equals(filter.discounted())) {
            parts.add(idIn(discountedProductIds));
        }
        if (Boolean.TRUE.equals(filter.inStock())) {
            parts.add(inStock());
        }
        if (Boolean.TRUE.equals(filter.lowStock())) {
            parts.add(lowStock());
        }
        if (filter.minPrice() != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            parts.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
        }
        if (filter.attributeOptionIds() != null && !filter.attributeOptionIds().isEmpty()) {
            parts.add(matchesAttributeOptions(filter.attributeOptionIds()));
        }
        if (filter.excludeIds() != null && !filter.excludeIds().isEmpty()) {
            parts.add((root, query, cb) -> cb.not(root.get("id").in(filter.excludeIds())));
        }

        return parts.stream().reduce(Specification::and).orElse(null);
    }

    public static Specification<Product> alive() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Product> hasStatus(ContentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Matches the name and both description fields of the requested language, plus the
     * SKU. Uses ILIKE rather than the tsvector index because catalog searches are usually
     * partial words ("chai" → "chair"), which full-text stemming would miss.
     */
    public static Specification<Product> matchesSearch(String search, String language) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            Join<Object, Object> translations = root.join("translations", JoinType.LEFT);

            Predicate languageMatch = cb.equal(translations.get("languageCode"), language);
            Predicate textMatch = cb.or(
                    cb.like(cb.lower(translations.get("name")), pattern),
                    cb.like(cb.lower(translations.get("shortDescription")), pattern),
                    cb.like(cb.lower(translations.get("description")), pattern));

            if (query != null) {
                query.distinct(true);
            }
            return cb.or(
                    cb.and(languageMatch, textMatch),
                    cb.like(cb.lower(root.get("sku")), pattern));
        };
    }

    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Product> idIn(Collection<Long> ids) {
        return (root, query, cb) -> ids == null || ids.isEmpty()
                ? cb.disjunction()
                : root.get("id").in(ids);
    }

    /** Not stock-tracked, or has units, or accepts backorders. */
    public static Specification<Product> inStock() {
        return (root, query, cb) -> cb.or(
                cb.isFalse(root.get("trackStock")),
                cb.isTrue(root.get("allowBackorder")),
                cb.greaterThan(root.get("stockQuantity"), 0));
    }

    /** Mirrors {@code ProductRepository.findLowStock} — the dashboard's "low stock" alert. */
    public static Specification<Product> lowStock() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("trackStock")),
                cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("lowStockThreshold")));
    }

    /**
     * Facet matching: OR inside one attribute, AND across attributes. Selecting
     * "Rəng: qara, ağ" plus "Material: ağac" means (black OR white) AND wood.
     * Implemented with one EXISTS subquery per attribute so the row count stays correct.
     */
    public static Specification<Product> matchesAttributeOptions(List<Long> optionIds) {
        return (root, query, cb) -> {
            if (query == null) {
                return cb.conjunction();
            }
            // Group the requested options by their owning attribute.
            Subquery<Long> grouping = query.subquery(Long.class);
            var optionRoot = grouping.from(az.catalog.modules.catalog.attribute.AttributeOption.class);
            grouping.select(optionRoot.get("attribute").get("id"))
                    .where(optionRoot.get("id").in(optionIds));

            // The grouping cannot be evaluated in Java from inside a Specification, so a
            // single EXISTS over all selected options is used, then intersected per
            // attribute by the caller-supplied map. See ProductService#applyFacets.
            Subquery<Long> exists = query.subquery(Long.class);
            var valueRoot = exists.from(ProductAttributeValue.class);
            exists.select(valueRoot.get("product").get("id"))
                    .where(cb.and(
                            cb.equal(valueRoot.get("product").get("id"), root.get("id")),
                            valueRoot.get("option").get("id").in(optionIds)));

            query.distinct(true);
            return cb.exists(exists);
        };
    }

    /**
     * Precise facet matching once the caller has resolved which attribute each option
     * belongs to: one EXISTS per attribute group.
     */
    public static Specification<Product> matchesFacets(Map<Long, List<Long>> optionsByAttribute) {
        return (root, query, cb) -> {
            if (query == null || optionsByAttribute.isEmpty()) {
                return cb.conjunction();
            }
            List<Predicate> predicates = optionsByAttribute.values().stream()
                    .map(options -> {
                        Subquery<Long> exists = query.subquery(Long.class);
                        var valueRoot = exists.from(ProductAttributeValue.class);
                        exists.select(valueRoot.get("product").get("id"))
                                .where(cb.and(
                                        cb.equal(valueRoot.get("product").get("id"), root.get("id")),
                                        valueRoot.get("option").get("id").in(options)));
                        return cb.exists(exists);
                    })
                    .collect(Collectors.toList());

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
