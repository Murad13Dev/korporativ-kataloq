package az.catalog.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated caller, reconstructed from the access token on every request.
 * Deliberately holds no password: nothing downstream ever needs one.
 */
public record AuthPrincipal(
        Long id,
        String email,
        String displayName,
        String role,
        PrincipalType type
) implements UserDetails {

    public static AuthPrincipal admin(Long id, String email, String displayName, String role) {
        return new AuthPrincipal(id, email, displayName, role, PrincipalType.ADMIN);
    }

    public static AuthPrincipal customer(Long id, String email, String displayName) {
        return new AuthPrincipal(id, email, displayName, "CUSTOMER", PrincipalType.CUSTOMER);
    }

    public boolean isAdmin() {
        return type == PrincipalType.ADMIN;
    }

    public boolean isCustomer() {
        return type == PrincipalType.CUSTOMER;
    }

    public boolean isSuperAdmin() {
        return isAdmin() && "SUPER_ADMIN".equals(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ROLE_ prefixed authority drives hasRole(...); the bare name keeps hasAuthority(...) usable.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
