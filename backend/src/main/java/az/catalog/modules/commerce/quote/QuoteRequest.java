package az.catalog.modules.commerce.quote;

import az.catalog.common.domain.BaseEntity;
import az.catalog.modules.commerce.company.Company;
import az.catalog.modules.commerce.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A negotiated-price request (RFQ) — the bulk-order counterpart to a normal cart
 * checkout. Nothing here touches stock or payment; accepting a quote is a status change
 * the sales team follows up on manually rather than an automatic order conversion, since
 * a negotiated deal usually still needs a human step (invoice, terms, PO number).
 */
@Entity
@Table(name = "quote_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "contact_name", nullable = false, length = 160)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 190)
    private String contactEmail;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "note", length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.PENDING;

    @Column(name = "admin_note", length = 2000)
    private String adminNote;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "quoted_total", precision = 14, scale = 2)
    private BigDecimal quotedTotal;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "AZN";

    @OneToMany(mappedBy = "quoteRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuoteRequestItem> items = new ArrayList<>();

    public boolean isExpired() {
        return validUntil != null && Instant.now().isAfter(validUntil);
    }
}
