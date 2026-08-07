package az.catalog.modules.catalog.review;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.catalog.product.ProductRepository;
import az.catalog.modules.commerce.customer.Customer;
import az.catalog.modules.commerce.customer.CustomerRepository;
import az.catalog.modules.commerce.order.OrderRepository;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ratings and comments on products.
 *
 * A review starts {@link ReviewStatus#PENDING} and is invisible to the storefront until
 * an admin approves it — see {@link #summary} and {@link #list}, which only ever look at
 * {@link ReviewStatus#APPROVED} rows. That keeps unmoderated content off a live site
 * without needing a separate "draft" concept the way products/pages have one.
 */
@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository repository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final LanguageService languageService;

    // =======================================================================
    // Storefront
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<ReviewDtos.ReviewDto> list(Long productId, Pageable pageable) {
        return repository.findByProductIdAndStatusOrderByCreatedAtDesc(productId, ReviewStatus.APPROVED, pageable)
                .map(ReviewDtos.ReviewDto::from);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.RatingSummary summary(Long productId) {
        List<Object[]> rows = repository.ratingSummary(productId);
        Object[] row = rows.isEmpty() ? new Object[]{0, 0L} : rows.get(0);
        double average = row[0] instanceof Number number ? number.doubleValue() : 0;
        long count = row[1] instanceof Number number ? number.longValue() : 0;

        Map<Integer, Long> breakdown = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            breakdown.put(star, 0L);
        }
        for (Object[] bucket : repository.ratingBreakdown(productId)) {
            breakdown.put((Integer) bucket[0], (Long) bucket[1]);
        }

        return new ReviewDtos.RatingSummary(Math.round(average * 10) / 10.0, count, breakdown);
    }

    @Transactional
    public ReviewDtos.ReviewDto create(Long customerId, ReviewDtos.CreateReviewRequest request) {
        Product product = productRepository.findAliveById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Məhsul", request.productId()));

        if (repository.existsByProductIdAndCustomerId(product.getId(), customerId)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu məhsul üçün artıq rəy yazmısınız");
        }

        Customer customer = customerRepository.findAliveById(customerId)
                .orElseThrow(() -> ApiException.notFound("Müştəri", customerId));

        boolean verified = orderRepository.existsDeliveredOrderForCustomerAndProduct(customerId, product.getId());

        ProductReview review = repository.save(ProductReview.builder()
                .product(product)
                .customer(customer)
                .authorName((customer.getFirstName() + " " + customer.getLastName()).trim())
                .rating(request.rating())
                .title(blankToNull(request.title()))
                .body(blankToNull(request.body()))
                .status(ReviewStatus.PENDING)
                .verifiedPurchase(verified)
                .build());

        return ReviewDtos.ReviewDto.from(review);
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<ReviewDtos.ReviewAdminDto> adminSearch(
            Long productId, ReviewStatus status, String search, Pageable pageable) {
        return repository.search(productId, status, blankToNull(search), pageable).map(this::toAdminDto);
    }

    @Transactional
    public ReviewDtos.ReviewAdminDto moderate(Long reviewId, ReviewStatus status) {
        ProductReview review = load(reviewId);
        review.setStatus(status);
        return toAdminDto(review);
    }

    @Transactional
    public ReviewDtos.ReviewAdminDto reply(Long reviewId, String reply) {
        ProductReview review = load(reviewId);
        review.setAdminReply(reply);
        review.setAdminReplyAt(OffsetDateTime.now());
        return toAdminDto(review);
    }

    @Transactional
    public void delete(Long reviewId) {
        repository.delete(load(reviewId));
    }

    private ProductReview load(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Rəy", id));
    }

    private ReviewDtos.ReviewAdminDto toAdminDto(ProductReview review) {
        Product product = review.getProduct();
        String fallback = languageService.defaultLanguageCode();
        var translation = product.translation(fallback, fallback);
        String productName = translation != null ? translation.getName() : product.getSku();

        return new ReviewDtos.ReviewAdminDto(
                review.getId(), product.getId(), productName, product.getSlug(),
                review.getCustomer() == null ? null : review.getCustomer().getId(),
                review.getAuthorName(), review.getRating(), review.getTitle(), review.getBody(),
                review.getStatus(), review.isVerifiedPurchase(), review.getAdminReply(),
                review.getAdminReplyAt(), review.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
