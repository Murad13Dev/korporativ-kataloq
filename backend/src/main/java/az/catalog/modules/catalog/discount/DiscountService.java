package az.catalog.modules.catalog.discount;

import az.catalog.common.config.CacheConfig;
import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountService {

    private static final int PREVIEW_SAMPLE_SIZE = 20;

    private final DiscountRepository repository;
    private final ProductRepository productRepository;
    private final DiscountResolver resolver;
    private final LanguageService languageService;

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<DiscountDtos.DiscountSummary> search(String search, ContentStatus status,
                                                     DiscountType type, DiscountScope scope,
                                                     Pageable pageable) {
        return repository.search(blankToNull(search), status, type, scope, pageable)
                .map(DiscountDtos.DiscountSummary::from);
    }

    @Transactional(readOnly = true)
    public DiscountDtos.DiscountResponse get(Long id) {
        return DiscountDtos.DiscountResponse.from(load(id));
    }

    /**
     * What the campaign does to real prices. Called from the admin form before saving so
     * a mistyped value is caught by the operator rather than by a customer.
     */
    @Transactional(readOnly = true)
    public DiscountDtos.PreviewResponse preview(Long id, String language) {
        Discount discount = load(id);
        String defaultLanguage = languageService.defaultLanguageCode();

        List<Product> candidates = switch (discount.getScope()) {
            case PRODUCTS -> discount.getProductIds().isEmpty()
                    ? List.of()
                    : productRepository.findAllPublishedByIds(discount.getProductIds());
            case CATEGORIES -> discount.getCategoryIds().isEmpty()
                    ? List.of()
                    : productRepository.findAllPublishedInCategoryTree(discount.getCategoryIds());
            case ALL_PRODUCTS -> productRepository.findAllPublishedWithPrice();
        };

        BigDecimal totalList = BigDecimal.ZERO;
        BigDecimal totalDiscounted = BigDecimal.ZERO;
        List<DiscountDtos.PreviewLine> sample = new ArrayList<>();

        for (Product product : candidates) {
            BigDecimal listPrice = product.getPrice();
            if (listPrice == null || listPrice.signum() <= 0 || !product.isShowPrice()) {
                continue;
            }
            PriceInfo price = PriceInfo.of(discount, listPrice, product.getCurrency());
            totalList = totalList.add(price.listPrice());
            totalDiscounted = totalDiscounted.add(price.finalPrice());

            if (sample.size() < PREVIEW_SAMPLE_SIZE) {
                var translation = product.translation(language, defaultLanguage);
                sample.add(new DiscountDtos.PreviewLine(
                        product.getId(), product.getSku(),
                        translation != null ? translation.getName() : product.getSku(),
                        price.listPrice(), price.finalPrice(), price.savingAmount(),
                        price.savingPercentage(), price.badgeLabel()));
            }
        }

        return new DiscountDtos.PreviewResponse(candidates.size(), totalList, totalDiscounted, sample);
    }

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_DISCOUNTS, allEntries = true)
    public DiscountDtos.DiscountResponse create(DiscountDtos.SaveRequest request) {
        Discount discount = new Discount();
        apply(discount, request);
        Discount saved = repository.save(discount);
        log.info("Created discount campaign '{}' ({} {})", saved.getName(),
                saved.getDiscountType(), saved.getValue());
        return DiscountDtos.DiscountResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_DISCOUNTS, allEntries = true)
    public DiscountDtos.DiscountResponse update(Long id, DiscountDtos.SaveRequest request) {
        Discount discount = load(id);
        apply(discount, request);
        return DiscountDtos.DiscountResponse.from(discount);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_DISCOUNTS, allEntries = true)
    public DiscountDtos.DiscountResponse changeStatus(Long id, ContentStatus status) {
        Discount discount = load(id);
        discount.setStatus(status);
        log.info("Discount {} status changed to {}", id, status);
        return DiscountDtos.DiscountResponse.from(discount);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_DISCOUNTS, allEntries = true)
    public void delete(Long id) {
        Discount discount = load(id);
        // Soft delete: cart_items and order_items reference the campaign for reporting.
        discount.softDelete();
        discount.setStatus(ContentStatus.ARCHIVED);
    }

    /**
     * Convenience path used by the product editor's "Endirim əlavə et" action: creates a
     * single-product campaign without making the operator visit the campaigns screen.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.ACTIVE_DISCOUNTS, allEntries = true)
    public DiscountDtos.DiscountResponse createForProduct(Long productId, DiscountDtos.SaveRequest request) {
        productRepository.findAliveById(productId)
                .orElseThrow(() -> ApiException.notFound("Məhsul", productId));

        Discount discount = new Discount();
        apply(discount, request);
        discount.setScope(DiscountScope.PRODUCTS);
        discount.setProductIds(new LinkedHashSet<>(Set.of(productId)));
        return DiscountDtos.DiscountResponse.from(repository.save(discount));
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void apply(Discount discount, DiscountDtos.SaveRequest request) {
        validate(request);

        discount.setName(request.name().trim());
        discount.setDescription(request.description());
        discount.setDiscountType(request.discountType());
        discount.setValue(request.value());
        discount.setMaxDiscountAmount(request.maxDiscountAmount());
        discount.setScope(request.scope());
        discount.setStartsAt(request.startsAt());
        discount.setEndsAt(request.endsAt());
        discount.setPriority(request.priority() == null ? 0 : request.priority());

        discount.setShowBadge(request.showBadge() == null || request.showBadge());
        discount.setBadgeLabel(blankToNull(request.badgeLabel()));
        discount.setBadgeStyle(request.badgeStyle() == null ? BadgeStyle.SALE : request.badgeStyle());
        discount.setBadgeColor(blankToNull(request.badgeColor()));
        discount.setShowCountdown(request.showCountdown() != null && request.showCountdown());
        discount.setStatus(request.status() == null ? ContentStatus.DRAFT : request.status());

        // Targets are replaced wholesale rather than merged: the admin form always sends
        // the complete selection, and a partial merge would silently keep stale targets.
        discount.setProductIds(request.scope() == DiscountScope.PRODUCTS
                ? new LinkedHashSet<>(nullSafe(request.productIds()))
                : new LinkedHashSet<>());
        discount.setCategoryIds(request.scope() == DiscountScope.CATEGORIES
                ? new LinkedHashSet<>(nullSafe(request.categoryIds()))
                : new LinkedHashSet<>());
    }

    private void validate(DiscountDtos.SaveRequest request) {
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
        if (request.endsAt() != null && request.endsAt().isBefore(OffsetDateTime.now())
                && request.status() == ContentStatus.ACTIVE) {
            throw ApiException.field(ErrorCode.DISCOUNT_INVALID_RANGE, "endsAt",
                    "Vaxtı keçmiş kampaniya aktiv edilə bilməz");
        }
        if (request.scope() == DiscountScope.PRODUCTS
                && (request.productIds() == null || request.productIds().isEmpty())) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "productIds",
                    "Ən azı bir məhsul seçilməlidir");
        }
        if (request.scope() == DiscountScope.CATEGORIES
                && (request.categoryIds() == null || request.categoryIds().isEmpty())) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "categoryIds",
                    "Ən azı bir kateqoriya seçilməlidir");
        }
        if (request.badgeStyle() == BadgeStyle.CUSTOM
                && (request.badgeColor() == null || request.badgeColor().isBlank())) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "badgeColor",
                    "CUSTOM stil üçün rəng seçilməlidir");
        }
    }

    private Discount load(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Endirim kampaniyası", id));
    }

    private static <T> Set<T> nullSafe(Set<T> value) {
        return value == null ? Set.of() : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
