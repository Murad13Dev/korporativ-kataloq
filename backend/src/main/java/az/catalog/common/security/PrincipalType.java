package az.catalog.common.security;

/**
 * The two identity domains in the platform. A token minted for one is never accepted
 * by the other: admin tokens cannot reach the storefront account API and vice versa.
 */
public enum PrincipalType {
    ADMIN,
    CUSTOMER
}
