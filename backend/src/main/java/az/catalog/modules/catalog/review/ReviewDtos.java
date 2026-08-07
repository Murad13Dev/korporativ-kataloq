package az.catalog.modules.catalog.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    // -----------------------------------------------------------------------
    // Storefront
    // -----------------------------------------------------------------------

    public record ReviewDto(
            Long id,
            String authorName,
            int rating,
            String title,
            String body,
            boolean verifiedPurchase,
            String adminReply,
            OffsetDateTime adminReplyAt,
            OffsetDateTime createdAt
    ) {
        public static ReviewDto from(ProductReview review) {
            return new ReviewDto(
                    review.getId(), review.getAuthorName(), review.getRating(), review.getTitle(),
                    review.getBody(), review.isVerifiedPurchase(), review.getAdminReply(),
                    review.getAdminReplyAt(), review.getCreatedAt());
        }
    }

    /**
     * @param breakdown count of approved reviews per star, 1..5, always present with zero
     *                  for stars nobody has given
     */
    public record RatingSummary(
            double average,
            long count,
            Map<Integer, Long> breakdown
    ) {
    }

    public record CreateReviewRequest(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 200) String title,
            @Size(max = 4000) String body
    ) {
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public record ReviewAdminDto(
            Long id,
            Long productId,
            String productName,
            String productSlug,
            Long customerId,
            String authorName,
            int rating,
            String title,
            String body,
            ReviewStatus status,
            boolean verifiedPurchase,
            String adminReply,
            OffsetDateTime adminReplyAt,
            OffsetDateTime createdAt
    ) {
    }

    public record ModerateRequest(@NotNull ReviewStatus status) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 4000) String reply) {
    }
}
