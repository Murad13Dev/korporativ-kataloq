package az.catalog.modules.commerce.cart;

import az.catalog.common.security.SecurityUtils;

/**
 * Who a cart belongs to. A logged-in customer wins over the session token, so signing in
 * on a second device shows the same cart rather than that device's guest cart.
 *
 * @param customerId {@code null} for anonymous visitors
 * @param sessionId  browser-held opaque token, sent as {@code X-Cart-Session}
 */
public record CartOwner(Long customerId, String sessionId) {

    public static CartOwner from(String sessionHeader) {
        return new CartOwner(SecurityUtils.currentCustomerId().orElse(null), sessionHeader);
    }

    public boolean isAnonymous() {
        return customerId == null;
    }
}
