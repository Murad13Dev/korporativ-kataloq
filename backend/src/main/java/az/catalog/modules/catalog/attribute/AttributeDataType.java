package az.catalog.modules.catalog.attribute;

/**
 * Determines which typed column on {@code product_attribute_values} may be filled.
 * Enforced by a check constraint in the schema and re-validated in the service.
 */
public enum AttributeDataType {
    /** {@code text_value} */
    TEXT,
    /** {@code number_value} — range filterable */
    NUMBER,
    /** {@code boolean_value} */
    BOOLEAN,
    /** {@code option_id}, single row */
    SELECT,
    /** {@code option_id}, one row per selected option — never a JSON array */
    MULTI_SELECT
}
