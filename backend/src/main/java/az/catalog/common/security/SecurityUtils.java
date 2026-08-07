package az.catalog.common.security;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Read-only access to the caller. Services use this instead of injecting
 * {@code Principal} into every signature.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public static AuthPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public static AuthPrincipal requireAdmin() {
        AuthPrincipal principal = requirePrincipal();
        if (!principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return principal;
    }

    public static AuthPrincipal requireCustomer() {
        AuthPrincipal principal = requirePrincipal();
        if (!principal.isCustomer()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return principal;
    }

    public static Optional<Long> currentAdminId() {
        return currentPrincipal().filter(AuthPrincipal::isAdmin).map(AuthPrincipal::id);
    }

    public static Optional<Long> currentCustomerId() {
        return currentPrincipal().filter(AuthPrincipal::isCustomer).map(AuthPrincipal::id);
    }
}
