package az.catalog.common.domain;

/**
 * Lifecycle shared by catalog and content entities. Only {@link #ACTIVE} rows reach the public API.
 */
public enum ContentStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
