package az.catalog.modules.commerce.quote;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.catalog.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One requested line. {@code productName} is a snapshot, same reasoning as order items. */
@Entity
@Table(name = "quote_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteRequestItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_request_id", nullable = false)
    private QuoteRequest quoteRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "quoted_unit_price", precision = 14, scale = 2)
    private BigDecimal quotedUnitPrice;
}
