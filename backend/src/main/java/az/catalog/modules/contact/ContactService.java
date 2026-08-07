package az.catalog.modules.contact;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.ratelimit.RateLimiterService;
import az.catalog.common.security.JwtService;
import az.catalog.common.security.SecurityUtils;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.common.util.RequestUtils;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.notification.MailService;
import az.catalog.modules.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Inbound leads and newsletter signups.
 *
 * Both endpoints are open to the internet, so submission goes through three filters
 * before a row is written: a honeypot field, a per-IP token bucket, and a per-address
 * count over the last hour. Each catches a different kind of abuse — a naive bot, a
 * flood from one machine, and a flood spread across many.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    /** Same address may not send more than this in an hour, whatever IP it comes from. */
    private static final int MAX_PER_EMAIL_PER_HOUR = 5;

    private final ContactRequestRepository requestRepository;
    private final NewsletterRepository newsletterRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RateLimiterService rateLimiter;
    private final MailService mailService;
    private final LanguageService languageService;
    private final HtmlSanitizer sanitizer;
    private final JwtService jwtService;
    private final AppProperties properties;

    // =======================================================================
    // Public submission
    // =======================================================================

    @Transactional
    public void submit(ContactDtos.SubmitRequest request, HttpServletRequest httpRequest) {
        guardSpam(request.website(), request.email(), httpRequest);

        ContactRequest saved = requestRepository.save(ContactRequest.builder()
                .name(clean(request.name()))
                .companyName(clean(request.companyName()))
                .email(request.email().trim().toLowerCase())
                .phone(clean(request.phone()))
                .subject(clean(request.subject()))
                .message(clean(request.message()))
                .requestType(request.requestType() == null
                        ? ContactRequest.RequestType.CONTACT : request.requestType())
                .privacyAccepted(request.privacyAccepted())
                .sourceUrl(request.sourceUrl())
                .ipAddress(RequestUtils.clientIp(httpRequest))
                .status(ContactRequest.Status.NEW)
                .build());

        mailService.sendContactRequestNotification(
                saved.getSubject() == null ? "Yeni sorğu" : saved.getSubject(),
                saved.getName(), saved.getEmail(), saved.getMessage(), saved.getId());

        log.info("Received contact request {} from {}", saved.getId(), saved.getEmail());
    }

    /** Inquiry raised from a product page; the product is attached for context. */
    @Transactional
    public void submitInquiry(Long productId, ContactDtos.InquiryRequest request,
                              HttpServletRequest httpRequest) {
        guardSpam(request.website(), request.email(), httpRequest);

        Product product = productRepository.findAliveById(productId)
                .orElseThrow(() -> ApiException.notFound("Məhsul", productId));

        String productName = Optional.ofNullable(
                        product.translation(languageService.defaultLanguageCode(),
                                languageService.defaultLanguageCode()))
                .map(translation -> translation.getName())
                .orElse(product.getSku());

        ContactRequest saved = requestRepository.save(ContactRequest.builder()
                .name(clean(request.name()))
                .companyName(clean(request.companyName()))
                .email(request.email().trim().toLowerCase())
                .phone(clean(request.phone()))
                .subject("Məhsul sorğusu: " + productName)
                .message(clean(request.message()))
                .requestType(ContactRequest.RequestType.PRODUCT_INQUIRY)
                .productId(productId)
                .privacyAccepted(request.privacyAccepted())
                .sourceUrl(request.sourceUrl())
                .ipAddress(RequestUtils.clientIp(httpRequest))
                .status(ContactRequest.Status.NEW)
                .build());

        mailService.sendContactRequestNotification(
                saved.getSubject(), saved.getName(), saved.getEmail(),
                saved.getMessage(), saved.getId());

        log.info("Received product inquiry {} for product {}", saved.getId(), productId);
    }

    // =======================================================================
    // Newsletter
    // =======================================================================

    /**
     * Always reports success. Telling an anonymous caller whether an address is already
     * subscribed would leak membership of the list.
     */
    @Transactional
    public void subscribe(ContactDtos.NewsletterRequest request, HttpServletRequest httpRequest) {
        if (request.website() != null && !request.website().isBlank()) {
            log.debug("Newsletter honeypot triggered");
            return;
        }
        rateLimiter.consume("newsletter:" + RequestUtils.clientIp(httpRequest), 5, Duration.ofHours(1));

        String email = request.email().trim().toLowerCase();
        NewsletterSubscription subscription = newsletterRepository.findByEmail(email)
                .orElseGet(() -> NewsletterSubscription.builder().email(email).build());

        if (subscription.getStatus() == NewsletterSubscription.Status.CONFIRMED) {
            return;
        }

        String rawToken = jwtService.generateOpaqueToken();
        subscription.setName(clean(request.name()));
        subscription.setLanguageCode(languageService.defaultLanguageCode());
        subscription.setStatus(NewsletterSubscription.Status.PENDING);
        subscription.setConfirmToken(jwtService.hashToken(rawToken));
        subscription.setUnsubscribedAt(null);
        subscription.setUpdatedAt(OffsetDateTime.now());

        newsletterRepository.save(subscription);

        mailService.sendNewsletterConfirmation(email,
                properties.getPublicUrl() + "/newsletter/confirm?token=" + rawToken);
    }

    @Transactional
    public boolean confirmSubscription(String rawToken) {
        return newsletterRepository.findByConfirmToken(jwtService.hashToken(rawToken))
                .map(subscription -> {
                    subscription.setStatus(NewsletterSubscription.Status.CONFIRMED);
                    subscription.setConfirmedAt(OffsetDateTime.now());
                    // The token is single use: burning it stops a forwarded link from
                    // re-subscribing someone who later unsubscribed.
                    subscription.setConfirmToken(null);
                    subscription.setUpdatedAt(OffsetDateTime.now());
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean unsubscribe(String email) {
        return newsletterRepository.findByEmail(email)
                .map(subscription -> {
                    subscription.setStatus(NewsletterSubscription.Status.UNSUBSCRIBED);
                    subscription.setUnsubscribedAt(OffsetDateTime.now());
                    subscription.setUpdatedAt(OffsetDateTime.now());
                    return true;
                })
                .orElse(false);
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<ContactDtos.RequestRow> search(String search, ContactRequest.Status status,
                                               ContactRequest.RequestType type,
                                               OffsetDateTime from, OffsetDateTime to,
                                               Pageable pageable) {
        return requestRepository.search(blankToNull(search), status, type, from, to, pageable)
                .map(this::toRow);
    }

    @Transactional(readOnly = true)
    public ContactDtos.RequestDetail get(Long id) {
        ContactRequest request = load(id);
        Product product = request.getProductId() == null ? null
                : productRepository.findAliveById(request.getProductId()).orElse(null);

        return new ContactDtos.RequestDetail(
                request.getId(), request.getName(), request.getCompanyName(),
                request.getEmail(), request.getPhone(), request.getSubject(), request.getMessage(),
                request.getRequestType(), request.getRequestType().label(),
                request.getStatus(), request.getStatus().label(),
                request.getProductId(), productName(product),
                product == null ? null : product.getSlug(),
                request.getAdminNote(), request.getHandledBy(),
                request.getHandledBy() == null ? null
                        : userRepository.findAliveById(request.getHandledBy())
                        .map(user -> user.fullName()).orElse(null),
                request.getHandledAt(), request.getSourceUrl(), request.getIpAddress(),
                request.getCreatedAt());
    }

    /**
     * Moving a request off NEW records who took it, so a shared inbox does not turn into
     * two people answering the same lead.
     */
    @Transactional
    public ContactDtos.RequestDetail changeStatus(Long id, ContactRequest.Status status) {
        ContactRequest request = load(id);
        request.setStatus(status);

        if (status != ContactRequest.Status.NEW && request.getHandledBy() == null) {
            request.setHandledBy(SecurityUtils.currentAdminId().orElse(null));
            request.setHandledAt(OffsetDateTime.now());
        }
        return get(id);
    }

    @Transactional
    public ContactDtos.RequestDetail setNote(Long id, String note) {
        load(id).setAdminNote(note);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        // Leads carry no downstream references, so a hard delete is safe here — unlike
        // orders, nothing else points at them.
        requestRepository.delete(load(id));
    }

    @Transactional(readOnly = true)
    public ContactDtos.Counters counters() {
        return new ContactDtos.Counters(
                requestRepository.count(),
                requestRepository.countByStatus(ContactRequest.Status.NEW),
                requestRepository.countByStatus(ContactRequest.Status.IN_PROGRESS),
                requestRepository.countByStatus(ContactRequest.Status.ANSWERED),
                requestRepository.countByStatus(ContactRequest.Status.SPAM));
    }

    @Transactional(readOnly = true)
    public Page<ContactDtos.SubscriberRow> subscribers(String search,
                                                       NewsletterSubscription.Status status,
                                                       Pageable pageable) {
        return newsletterRepository.search(blankToNull(search), status, pageable)
                .map(subscription -> new ContactDtos.SubscriberRow(
                        subscription.getId(), subscription.getEmail(), subscription.getName(),
                        subscription.getStatus(), subscription.getConfirmedAt(),
                        subscription.getCreatedAt()));
    }

    // =======================================================================
    // Internals
    // =======================================================================

    /**
     * Three independent filters. The honeypot costs nothing and stops naive bots; the
     * IP bucket stops a flood from one machine; the per-address count stops the same
     * sender rotating IPs.
     */
    private void guardSpam(String honeypot, String email, HttpServletRequest httpRequest) {
        if (honeypot != null && !honeypot.isBlank()) {
            // Answered as if accepted — telling a bot it was detected only helps it adapt.
            log.debug("Contact honeypot triggered from {}", RequestUtils.clientIp(httpRequest));
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Sorğu qəbul edilmədi");
        }

        rateLimiter.consume("contact:" + RequestUtils.clientIp(httpRequest),
                properties.getSecurity().getRateLimit().getPublicFormSubmissionsPerHour(),
                Duration.ofHours(1));

        long recent = requestRepository.countRecentFromEmail(
                email.trim().toLowerCase(), OffsetDateTime.now().minusHours(1));
        if (recent >= MAX_PER_EMAIL_PER_HOUR) {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Bu e-poçtdan çox sayda sorğu göndərilib. Bir az sonra yenidən cəhd edin.");
        }
    }

    private ContactDtos.RequestRow toRow(ContactRequest request) {
        Product product = request.getProductId() == null ? null
                : productRepository.findAliveById(request.getProductId()).orElse(null);

        return new ContactDtos.RequestRow(
                request.getId(), request.getName(), request.getCompanyName(),
                request.getEmail(), request.getPhone(), request.getSubject(),
                request.getRequestType(), request.getRequestType().label(),
                request.getStatus(), request.getStatus().label(),
                request.getProductId(), productName(product), request.getCreatedAt());
    }

    private String productName(Product product) {
        if (product == null) {
            return null;
        }
        String defaultLanguage = languageService.defaultLanguageCode();
        var translation = product.translation(defaultLanguage, defaultLanguage);
        return translation != null ? translation.getName() : product.getSku();
    }

    private ContactRequest load(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Əlaqə sorğusu", id));
    }

    private String clean(String value) {
        return value == null ? null : sanitizer.stripTags(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
