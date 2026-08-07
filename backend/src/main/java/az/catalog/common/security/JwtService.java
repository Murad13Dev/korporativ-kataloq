package az.catalog.common.security;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies stateless access tokens. Refresh tokens are deliberately NOT JWTs —
 * they are opaque random strings whose SHA-256 hash is stored, so a leaked database
 * cannot be replayed and a logout can revoke a specific session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";

    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes. Set the JWT_SECRET environment variable.");
        }
        if (secret.startsWith("change-me")) {
            log.warn("!! JWT_SECRET is still the development default. Set a real secret before deploying. !!");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getSecurity().getJwt().getAccessTokenTtl());

        return Jwts.builder()
                .subject(String.valueOf(principal.id()))
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .claims(Map.of(
                        CLAIM_TYPE, principal.type().name(),
                        CLAIM_ROLE, principal.role(),
                        CLAIM_EMAIL, principal.email(),
                        CLAIM_NAME, principal.displayName() == null ? "" : principal.displayName()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.getSecurity().getJwt().getAccessTokenTtl().toSeconds();
    }

    /**
     * @return the principal encoded in the token
     * @throws ApiException with {@link ErrorCode#TOKEN_EXPIRED} or {@link ErrorCode#TOKEN_INVALID}
     */
    public AuthPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getSecurity().getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            PrincipalType type = PrincipalType.valueOf(claims.get(CLAIM_TYPE, String.class));
            return new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    claims.get(CLAIM_ROLE, String.class),
                    type);
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
    }

    // --- opaque refresh tokens ------------------------------------------------

    /** 512 bits of entropy, URL-safe. Only this value ever leaves the server. */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Storage form of an opaque token. Lookups hash the presented value and compare. */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
