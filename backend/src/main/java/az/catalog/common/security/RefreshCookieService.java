package az.catalog.common.security;

import az.catalog.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Owns the HttpOnly refresh-token cookie. Admin and customer sessions use different
 * cookie names so an operator can stay signed into the panel and the storefront at once.
 */
@Service
@RequiredArgsConstructor
public class RefreshCookieService {

    private final AppProperties properties;

    public String cookieName(PrincipalType type) {
        String base = properties.getSecurity().getRefreshCookie().getName();
        return type == PrincipalType.CUSTOMER ? base + "_c" : base;
    }

    public void write(HttpServletResponse response, PrincipalType type, String rawToken, Duration ttl) {
        var config = properties.getSecurity().getRefreshCookie();
        ResponseCookie cookie = ResponseCookie.from(cookieName(type), rawToken)
                .httpOnly(true)
                .secure(config.isSecure())
                .path(config.getPath())
                .maxAge(ttl)
                .sameSite(config.getSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response, PrincipalType type) {
        var config = properties.getSecurity().getRefreshCookie();
        ResponseCookie cookie = ResponseCookie.from(cookieName(type), "")
                .httpOnly(true)
                .secure(config.isSecure())
                .path(config.getPath())
                .maxAge(0)
                .sameSite(config.getSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> read(HttpServletRequest request, PrincipalType type) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        String name = cookieName(type);
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
