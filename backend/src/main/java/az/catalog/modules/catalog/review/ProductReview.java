package az.catalog.modules.catalog.review;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.catalog.product.Product;
import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A shopper's rating and comment on a product.
 *
 * Starts {@link ReviewStatus#PENDING} and only counts toward the public average once an
 * admin approves it — unmoderated reviews are how a storefront's first impression becomes
 * spam. {@code verifiedPurchase} is computed server-side from the customer's own delivered
 * orders at submission time; it is never accepted from the request.
 */
@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "author_name", nullable = false, length = 160)
    private String authorName;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Column(name = "admin_reply", columnDefinition = "text")
    private String adminReply;

    @Column(name = "admin_reply_at")
    private OffsetDateTime adminReplyAt;
}
