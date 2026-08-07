package az.catalog.modules.commerce.company;

import az.catalog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A B2B account. Its {@link CompanyStatus} gates whether {@link #discountPercentage}
 * actually applies at checkout — see {@code CheckoutService} — so a company sitting in
 * PENDING costs nothing until an admin has verified it.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company extends BaseEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "tax_number", length = 64)
    private String taxNumber;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.PENDING;
}
