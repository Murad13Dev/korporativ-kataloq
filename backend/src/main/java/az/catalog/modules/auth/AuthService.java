package az.catalog.modules.auth;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.ratelimit.RateLimiterService;
import az.catalog.common.security.AuthPrincipal;
import az.catalog.common.security.JwtService;
import az.catalog.common.security.PasswordPolicy;
import az.catalog.common.security.PrincipalType;
import az.catalog.common.security.RefreshCookieService;
import az.catalog.common.util.RequestUtils;
import az.catalog.modules.notification.MailService;
import az.catalog.modules.user.User;
import az.catalog.modules.user.UserRepository;
import az.catalog.modules.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final RefreshCookieService refreshCookieService;
    private final RateLimiterService rateLimiter;
    private final MailService mailService;
    private final AppProperties properties;

    // -----------------------------------------------------------------------
    // Login / refresh / logout
    // -----------------------------------------------------------------------

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request,
                                        HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        String ip = RequestUtils.clientIp(httpRequest);
        rateLimiter.consume("admin-login:" + ip,
                properties.getSecurity().getRateLimit().getLoginAttemptsPerMinute(), Duration.ofMinutes(1));

        User user = userRepository.findActiveByEmail(request.email())
                .orElse(null);

        // Hash even when the user does not exist so response time cannot be used to
        // enumerate valid admin addresses.
        if (user == null) {
            passwordEncoder.matches(request.password(), "$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin");
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }

        rateLimiter.reset("admin-login:" + ip);
        user.setLastLoginAt(OffsetDateTime.now());

        issueRefreshToken(user, httpRequest, httpResponse);
        return new AuthDtos.LoginResponse(
                jwtService.issueAccessToken(toPrincipal(user)),
                jwtService.accessTokenTtlSeconds(),
                AuthDtos.AuthenticatedUser.from(user));
    }

    /**
     * Rotates the refresh token: the presented one is revoked and a fresh one issued.
     * Presenting an already-revoked token means it was replayed, so every session for
     * that user is terminated.
     */
    @Transactional
    public AuthDtos.LoginResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawToken = refreshCookieService.read(httpRequest, PrincipalType.ADMIN)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));

        RefreshToken stored = refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawToken))
                .orElseThrow(() -> {
                    refreshCookieService.clear(httpResponse, PrincipalType.ADMIN);
                    return new ApiException(ErrorCode.TOKEN_INVALID);
                });

        if (stored.getRevokedAt() != null) {
            log.warn("Replayed admin refresh token for user {} — revoking all sessions", stored.getUserId());
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), OffsetDateTime.now());
            refreshCookieService.clear(httpResponse, PrincipalType.ADMIN);
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (!stored.isUsable()) {
            refreshCookieService.clear(httpResponse, PrincipalType.ADMIN);
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findAliveById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }

        stored.revoke();
        issueRefreshToken(user, httpRequest, httpResponse);

        return new AuthDtos.LoginResponse(
                jwtService.issueAccessToken(toPrincipal(user)),
                jwtService.accessTokenTtlSeconds(),
                AuthDtos.AuthenticatedUser.from(user));
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        refreshCookieService.read(httpRequest, PrincipalType.ADMIN)
                .flatMap(raw -> refreshTokenRepository.findByTokenHash(jwtService.hashToken(raw)))
                .ifPresent(RefreshToken::revoke);
        refreshCookieService.clear(httpResponse, PrincipalType.ADMIN);
    }

    @Transactional
    public void logoutAllSessions(Long userId, HttpServletResponse httpResponse) {
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
        refreshCookieService.clear(httpResponse, PrincipalType.ADMIN);
    }

    // -----------------------------------------------------------------------
    // Profile
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AuthDtos.AuthenticatedUser currentUser(Long userId) {
        return AuthDtos.AuthenticatedUser.from(loadActive(userId));
    }

    @Transactional
    public AuthDtos.AuthenticatedUser updateProfile(Long userId, AuthDtos.UpdateProfileRequest request) {
        User user = loadActive(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(request.locale());
        }
        user.setAvatarMediaId(request.avatarMediaId());
        return AuthDtos.AuthenticatedUser.from(user);
    }

    @Transactional
    public void changePassword(Long userId, AuthDtos.ChangePasswordRequest request,
                               HttpServletResponse httpResponse) {
        User user = loadActive(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Cari şifrə yanlışdır");
        }
        passwordPolicy.validate(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // A password change ends every other session.
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
        refreshCookieService.clear(httpResponse, PrincipalType.ADMIN);
    }

    // -----------------------------------------------------------------------
    // Password reset
    // -----------------------------------------------------------------------

    /**
     * Always reports success. Telling an anonymous caller whether an address is registered
     * would turn this endpoint into an account enumeration oracle.
     */
    @Transactional
    public void requestPasswordReset(AuthDtos.ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimiter.consume("admin-forgot:" + RequestUtils.clientIp(httpRequest), 3, Duration.ofMinutes(15));

        Optional<User> maybeUser = userRepository.findActiveByEmail(request.email());
        if (maybeUser.isEmpty() || maybeUser.get().getStatus() != UserStatus.ACTIVE) {
            log.debug("Password reset requested for unknown or inactive address");
            return;
        }
        User user = maybeUser.get();
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), OffsetDateTime.now());

        String rawToken = jwtService.generateOpaqueToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(jwtService.hashToken(rawToken))
                .expiresAt(OffsetDateTime.now().plus(RESET_TOKEN_TTL))
                .createdAt(OffsetDateTime.now())
                .build());

        String link = properties.getPublicUrl() + "/admin/reset-password?token=" + rawToken;
        mailService.sendPasswordReset(user.getEmail(), user.fullName(), link, RESET_TOKEN_TTL.toHours());
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(jwtService.hashToken(request.token()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (!token.isUsable()) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        passwordPolicy.validate(request.password());

        User user = userRepository.findAliveById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        token.setUsedAt(OffsetDateTime.now());

        refreshTokenRepository.revokeAllForUser(user.getId(), OffsetDateTime.now());
        log.info("Password reset completed for admin {}", user.getId());
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void issueRefreshToken(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Duration ttl = properties.getSecurity().getJwt().getRefreshTokenTtl();
        String rawToken = jwtService.generateOpaqueToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(jwtService.hashToken(rawToken))
                .userAgent(RequestUtils.userAgent(httpRequest))
                .ipAddress(RequestUtils.clientIp(httpRequest))
                .expiresAt(OffsetDateTime.now().plus(ttl))
                .createdAt(OffsetDateTime.now())
                .build());

        refreshCookieService.write(httpResponse, PrincipalType.ADMIN, rawToken, ttl);
    }

    private AuthPrincipal toPrincipal(User user) {
        return AuthPrincipal.admin(user.getId(), user.getEmail(), user.fullName(), user.getRole().name());
    }

    private User loadActive(Long userId) {
        User user = userRepository.findAliveById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return user;
    }

    /** Keeps the token tables from growing without bound. Runs nightly at 03:15. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeStaleTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
        int refresh = refreshTokenRepository.deleteExpired(cutoff);
        int reset = passwordResetTokenRepository.deleteExpired(cutoff);
        if (refresh + reset > 0) {
            log.info("Purged {} refresh and {} reset tokens older than {}", refresh, reset, cutoff);
        }
    }
}
