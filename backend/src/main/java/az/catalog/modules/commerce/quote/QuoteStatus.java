package az.catalog.modules.commerce.quote;

public enum QuoteStatus {
    /** Just submitted; waiting on an admin to price it. */
    PENDING,
    /** Admin has set line prices and a validity window. */
    QUOTED,
    /** Customer accepted the quote. */
    ACCEPTED,
    DECLINED,
    /** Passed its {@code validUntil} without being accepted. */
    EXPIRED
}
