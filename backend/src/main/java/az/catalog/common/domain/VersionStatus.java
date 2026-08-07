package az.catalog.common.domain;

/**
 * Draft / publish workflow used by site configuration, pages and menus.
 * At most one {@link #PUBLISHED} and one {@link #DRAFT} row may exist per versioned aggregate;
 * this is enforced by partial unique indexes in the schema and re-checked in the services.
 */
public enum VersionStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
