package az.catalog.modules.commerce.customer;

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
import az.catalog.modules.commerce.cart.CartService;
import az.catalog.modules.notification.MailService;
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

/**
 * Storefront account authentication.
 *
 * Mirrors the admin flow (opaque rotating refresh token in an HttpOnly cookie) but with
 * its own cookie name and token audience, so a customer token can never reach
 * {@code /api/admin} and an operator can be signed into both at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);

    private final CustomerRepository customerRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final CustomerPasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final RefreshCookieService refreshCookieService;
    private final RateLimiterService rateLimiter;
    private final CartService cartService;
    private final MailService mailService;
    private final AppProperties properties;

    @Transactional
    public CustomerDtos.AuthResponse register(CustomerDtos.RegisterRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        rateLimiter.consume("customer-register:" + RequestUtils.clientIp(httpRequest), 5, Duration.ofHours(1));

        String email = request.email().trim().toLowerCase();
        passwordPolicy.validate(request.password());

        Customer customer = customerRepository.findAliveByEmail(email).orElse(null);

        if (customer != null && customer.getPasswordHash() != null) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (customer == null) {
            customer = Customer.builder().email(email).build();
        }

        // A guest record from an earlier checkout is upgraded in place, so the shopper
        // keeps the order history they created before registering.
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setPhone(request.phone());
        customer.setCompanyName(request.companyName());
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        customer.setStatus(Customer.Status.ACTIVE);
        customer.setMarketingOptIn(Boolean.TRUE.equals(request.marketingOptIn()));
        customer.setLastLoginAt(OffsetDateTime.now());

        Customer saved = customerRepository.save(customer);
        cartService.mergeGuestCart(request.cartSessionId(), saved.getId());

        mailService.sendWelcome(saved.getEmail(), saved.fullName());
        log.info("Registered customer {}", saved.getId());

        return authenticate(saved, httpRequest, httpResponse);
    }

    @Transactional
    public CustomerDtos.AuthResponse login(CustomerDtos.LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        String ip = RequestUtils.clientIp(httpRequest);
        rateLimiter.consume("customer-login:" + ip,
                properties.getSecurity().getRateLimit().getLoginAttemptsPerMinute(), Duration.ofMinutes(1));

        Customer customer = customerRepository.findAliveByEmail(request.email().trim()).orElse(null);

        // Hash regardless so timing cannot be used to enumerate registered addresses.
        if (customer == null || customer.getPasswordHash() == null) {
            passwordEncoder.matches(request.password(),
                    "$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin");
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (customer.getStatus() == Customer.Status.BLOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE, "Hesab bloklanıb");
        }
        if (customer.getStatus() == Customer.Status.INACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }

        rateLimiter.reset("customer-login:" + ip);
        customer.setLastLoginAt(OffsetDateTime.now());
        cartService.mergeGuestCart(request.cartSessionId(), customer.getId());

        return authenticate(customer, httpRequest, httpResponse);
    }

    @Transactional
    public CustomerDtos.AuthResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawToken = refreshCookieService.read(httpRequest, PrincipalType.CUSTOMER)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));

        CustomerRefreshToken stored = refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawToken))
                .orElseThrow(() -> {
                    refreshCookieService.clear(httpResponse, PrincipalType.CUSTOMER);
                    return new ApiException(ErrorCode.TOKEN_INVALID);
                });

        if (stored.getRevokedAt() != null) {
            // Replay of a rotated token: assume compromise and end every session.
            log.warn("Replayed customer refresh token for {} — revoking all sessions", stored.getCustomerId());
            refreshTokenRepository.revokeAllForCustomer(stored.getCustomerId(), OffsetDateTime.now());
            refreshCookieService.clear(httpResponse, PrincipalType.CUSTOMER);
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (!stored.isUsable()) {
            refreshCookieService.clear(httpResponse, PrincipalType.CUSTOMER);
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        Customer customer = customerRepository.findAliveById(stored.getCustomerId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        if (!customer.canLogin()) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }

        stored.revoke();
        return authenticate(customer, httpRequest, httpResponse);
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        refreshCookieService.read(httpRequest, PrincipalType.CUSTOMER)
                .flatMap(raw -> refreshTokenRepository.findByTokenHash(jwtService.hashToken(raw)))
                .ifPresent(CustomerRefreshToken::revoke);
        refreshCookieService.clear(httpResponse, PrincipalType.CUSTOMER);
    }

    // --- profile ------------------------------------------------------------

    @Transactional(readOnly = true)
    public CustomerDtos.CustomerProfile profile(Long customerId) {
        return CustomerDtos.CustomerProfile.from(load(customerId));
    }

    @Transactional
    public CustomerDtos.CustomerProfile updateProfile(Long customerId,
                                                      CustomerDtos.UpdateProfileRequest request) {
        Customer customer = load(customerId);
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setPhone(request.phone());
        customer.setCompanyName(request.companyName());
        customer.setTaxNumber(request.taxNumber());
        if (request.customerType() != null) {
            customer.setCustomerType(request.customerType());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            customer.setLocale(request.locale());
        }
        if (request.marketingOptIn() != null) {
            customer.setMarketingOptIn(request.marketingOptIn());
        }
        return CustomerDtos.CustomerProfile.from(customer);
    }

    @Transactional
    public void changePassword(Long customerId, CustomerDtos.ChangePasswordRequest request,
                               HttpServletResponse httpResponse) {
        Customer customer = load(customerId);
        if (customer.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), customer.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Cari şifrə yanlışdır");
        }
        passwordPolicy.validate(request.newPassword());
        customer.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        refreshTokenRepository.revokeAllForCustomer(customerId, OffsetDateTime.now());
        refreshCookieService.clear(httpResponse, PrincipalType.CUSTOMER);
    }

    // --- password reset -------------------------------------------------------

    /**
     * Always reports success. Telling an anonymous caller whether an address is registered
     * would turn this endpoint into an account enumeration oracle.
     */
    @Transactional
    public void requestPasswordReset(CustomerDtos.ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimiter.consume("customer-forgot:" + RequestUtils.clientIp(httpRequest), 3, Duration.ofMinutes(15));

        Customer customer = customerRepository.findAliveByEmail(request.email().trim().toLowerCase()).orElse(null);
        if (customer == null || !customer.canLogin()) {
            log.debug("Password reset requested for unknown or guest-only address");
            return;
        }

        passwordResetTokenRepository.invalidateAllForCustomer(customer.getId(), OffsetDateTime.now());

        String rawToken = jwtService.generateOpaqueToken();
        passwordResetTokenRepository.save(CustomerPasswordResetToken.builder()
                .customerId(customer.getId())
                .tokenHash(jwtService.hashToken(rawToken))
                .expiresAt(OffsetDateTime.now().plus(RESET_TOKEN_TTL))
                .createdAt(OffsetDateTime.now())
                .build());

        String link = properties.getPublicUrl() + "/hesab/sifre-yenile?token=" + rawToken;
        mailService.sendPasswordReset(customer.getEmail(), customer.fullName(), link, RESET_TOKEN_TTL.toHours());
    }

    @Transactional
    public void resetPassword(CustomerDtos.ResetPasswordRequest request) {
        CustomerPasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(jwtService.hashToken(request.token()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (!token.isUsable()) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        passwordPolicy.validate(request.password());

        Customer customer = customerRepository.findAliveById(token.getCustomerId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        token.setUsedAt(OffsetDateTime.now());

        refreshTokenRepository.revokeAllForCustomer(customer.getId(), OffsetDateTime.now());
        log.info("Password reset completed for customer {}", customer.getId());
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeStaleTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
        int refresh = refreshTokenRepository.deleteExpired(cutoff);
        int reset = passwordResetTokenRepository.deleteExpired(cutoff);
        if (refresh + reset > 0) {
            log.info("Purged {} refresh and {} customer reset tokens older than {}", refresh, reset, cutoff);
        }
    }

    // --- internals ----------------------------------------------------------

    private CustomerDtos.AuthResponse authenticate(Customer customer, HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        Duration ttl = properties.getSecurity().getJwt().getRefreshTokenTtl();
        String rawToken = jwtService.generateOpaqueToken();

        refreshTokenRepository.save(CustomerRefreshToken.builder()
                .customerId(customer.getId())
                .tokenHash(jwtService.hashToken(rawToken))
                .userAgent(RequestUtils.userAgent(httpRequest))
                .ipAddress(RequestUtils.clientIp(httpRequest))
                .expiresAt(OffsetDateTime.now().plus(ttl))
                .build());

        refreshCookieService.write(httpResponse, PrincipalType.CUSTOMER, rawToken, ttl);

        AuthPrincipal principal = AuthPrincipal.customer(
                customer.getId(), customer.getEmail(), customer.fullName());

        return new CustomerDtos.AuthResponse(
                jwtService.issueAccessToken(principal),
                jwtService.accessTokenTtlSeconds(),
                CustomerDtos.CustomerProfile.from(customer));
    }

    private Customer load(Long customerId) {
        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (customer.getStatus() == Customer.Status.BLOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return customer;
    }
}
