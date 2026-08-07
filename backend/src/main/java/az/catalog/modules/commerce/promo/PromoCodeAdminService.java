package az.catalog.modules.commerce.promo;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.discount.DiscountType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Admin CRUD for cart-level vouchers.
 *
 * Kept separate from {@link PromoCodeService}, which is the storefront-facing half
 * (validate / redeem / release). Mixing them would put checkout's hot path in the same
 * class as rarely-used management code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeAdminService {

    /** Ambiguous characters (0/O, 1/I/L) are excluded — codes get read aloud and mistyped. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PromoCodeRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<PromoCodeDtos.PromoCodeResponse> search(String search, ContentStatus status,
                                                        Pageable pageable) {
        return repository.search(blankToNull(search), status, pageable)
                .map(PromoCodeDtos.PromoCodeResponse::from);
    }

    @Transactional(readOnly = true)
    public PromoCodeDtos.PromoCodeResponse get(Long id) {
        return PromoCodeDtos.PromoCodeResponse.from(load(id));
    }

    @Transactional
    public PromoCodeDtos.PromoCodeResponse create(PromoCodeDtos.SaveRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCode(code, null)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur: " + code);
        }

        PromoCode promo = new PromoCode();
        promo.setCode(code);
        apply(promo, request);

        PromoCode saved = repository.save(promo);
        log.info("Created promo code {} ({} {})", saved.getCode(), saved.getDiscountType(), saved.getValue());
        return PromoCodeDtos.PromoCodeResponse.from(saved);
    }

    @Transactional
    public PromoCodeDtos.PromoCodeResponse update(Long id, PromoCodeDtos.SaveRequest request) {
        PromoCode promo = load(id);
        String code = request.code().trim().toUpperCase();

        // Renaming a code that customers already hold would silently invalidate flyers,
        // e-mails and anything else it was printed on.
        if (!promo.getCode().equalsIgnoreCase(code) && promo.getUsedCount() > 0) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "İstifadə olunmuş promo kodun adı dəyişdirilə bilməz");
        }
        if (repository.existsByCode(code, id)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu kod artıq mövcuddur");
        }

        promo.setCode(code);
        apply(promo, request);
        return PromoCodeDtos.PromoCodeResponse.from(promo);
    }

    @Transactional
    public PromoCodeDtos.PromoCodeResponse changeStatus(Long id, ContentStatus status) {
        PromoCode promo = load(id);
        promo.setStatus(status);
        return PromoCodeDtos.PromoCodeResponse.from(promo);
    }

    @Transactional
    public void delete(Long id) {
        PromoCode promo = load(id);
        // Soft delete: redemption rows reference the code for reporting.
        promo.softDelete();
        promo.setStatus(ContentStatus.ARCHIVED);
    }

    /** Redemption history for one code. */
    @Transactional(readOnly = true)
    public List<PromoCodeDtos.RedemptionResponse> redemptions(Long id) {
        return jdbcTemplate.query("""
                SELECT r.id, r.order_id, o.order_number, r.customer_id,
                       COALESCE(r.customer_email, o.customer_email) AS customer_email,
                       r.amount, r.created_at
                FROM promo_code_redemptions r
                LEFT JOIN orders o ON o.id = r.order_id
                WHERE r.promo_code_id = ?
                ORDER BY r.created_at DESC
                LIMIT 100
                """,
                (rs, rowNum) -> new PromoCodeDtos.RedemptionResponse(
                        rs.getLong("id"),
                        rs.getObject("order_id", Long.class),
                        rs.getString("order_number"),
                        rs.getObject("customer_id", Long.class),
                        rs.getString("customer_email"),
                        rs.getBigDecimal("amount"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                id);
    }

    /** Suggests an unused code for the "generate" button in the admin form. */
    @Transactional(readOnly = true)
    public String generateCode(String prefix) {
        String cleanPrefix = prefix == null ? "" : prefix.replaceAll("[^A-Za-z0-9]", "").toUpperCase();

        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder builder = new StringBuilder(cleanPrefix);
            for (int i = 0; i < 8; i++) {
                builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = builder.toString();
            if (!repository.existsByCode(candidate, null)) {
                return candidate;
            }
        }
        return cleanPrefix + System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------

    private void apply(PromoCode promo, PromoCodeDtos.SaveRequest request) {
        validate(request);

        promo.setDescription(blankToNull(request.description()));
        promo.setDiscountType(request.discountType());
        promo.setValue(request.value());
        promo.setMaxDiscountAmount(request.maxDiscountAmount());
        promo.setMinSubtotal(request.minSubtotal() == null ? BigDecimal.ZERO : request.minSubtotal());
        promo.setFreeDelivery(Boolean.TRUE.equals(request.freeDelivery()));
        promo.setStartsAt(request.startsAt());
        promo.setEndsAt(request.endsAt());
        promo.setUsageLimit(request.usageLimit());
        promo.setUsageLimitPerCustomer(
                request.usageLimitPerCustomer() == null ? 1 : request.usageLimitPerCustomer());
        promo.setExcludeDiscounted(Boolean.TRUE.equals(request.excludeDiscounted()));
        promo.setStatus(request.status() == null ? ContentStatus.DRAFT : request.status());
    }

    private void validate(PromoCodeDtos.SaveRequest request) {
        boolean freeDelivery = Boolean.TRUE.equals(request.freeDelivery());

        // A free-delivery code may legitimately carry a zero value; anything else may not.
        if (!freeDelivery && request.value().signum() <= 0) {
            throw ApiException.field(ErrorCode.DISCOUNT_INVALID_VALUE, "value",
                    "Endirim dəyəri 0-dan böyük olmalıdır");
        }
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.field(ErrorCode.DISCOUNT_INVALID_VALUE, "value",
                    "Faiz endirimi 100-dən böyük ola bilməz");
        }
        if (request.startsAt() != null && request.endsAt() != null
                && !request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.field(ErrorCode.DISCOUNT_INVALID_RANGE, "endsAt",
                    "Bitmə tarixi başlama tarixindən sonra olmalıdır");
        }
        if (request.usageLimit() != null && request.usageLimitPerCustomer() != null
                && request.usageLimitPerCustomer() > request.usageLimit()) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "usageLimitPerCustomer",
                    "Müştəri limiti ümumi limitdən böyük ola bilməz");
        }
    }

    private PromoCode load(Long id) {
        return repository.findAliveById(id)
                .orElseThrow(() -> ApiException.notFound("Promo kod", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
