-- =============================================================================
-- V20 : Typo-tolerant search
--
-- The catalog search stays substring-based (ILIKE) for the common case — see the
-- javadoc on ProductSpecifications.matchesSearch for why stemmed full-text search
-- would break partial-word queries like "chai" -> "chair". Trigram similarity adds
-- a fallback layer only: when the substring search comes back empty, the app tries
-- a fuzzy match so a misspelling ("krslo" for "kreslo") doesn't return nothing.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_product_tr_name_trgm ON product_translations USING gin (name gin_trgm_ops);
CREATE INDEX idx_products_sku_trgm ON products USING gin (sku gin_trgm_ops);
