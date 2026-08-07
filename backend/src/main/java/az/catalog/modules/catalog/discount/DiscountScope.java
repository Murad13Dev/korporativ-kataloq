package az.catalog.modules.catalog.discount;

/**
 * What a campaign targets. Evaluated most-specific-first when priorities tie:
 * an explicit product beats a category, which beats a store-wide sale.
 */
public enum DiscountScope {
    /** Every purchasable product. */
    ALL_PRODUCTS,
    /** The listed categories (optionally including their descendants). */
    CATEGORIES,
    /** Only the explicitly listed products. */
    PRODUCTS
}
