package az.catalog.modules.catalog.discount;

/**
 * Visual treatment of the ribbon on the product card. The storefront maps each value to
 * a token from the active theme rather than to a hard-coded colour, so ribbons stay on
 * brand for every client installation.
 */
public enum BadgeStyle {
    /** Default sale ribbon — theme `saleColor`. */
    SALE,
    /** High-urgency deal — warm gradient. */
    HOT,
    /** Seasonal campaign — accent colour. */
    SEASONAL,
    /** Clearance / stock run-out — muted dark. */
    CLEARANCE,
    /** Bundle or multi-buy offer. */
    BUNDLE,
    /** Uses the campaign's explicit `badgeColor`. */
    CUSTOM
}
