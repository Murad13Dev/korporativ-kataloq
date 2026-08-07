package az.catalog.modules.commerce.company;

import az.catalog.common.domain.BaseEntity;
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

/** Links a {@link Customer} to the one {@link Company} they belong to. */
@Entity
@Table(name = "company_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    @Builder.Default
    private CompanyRole role = CompanyRole.BUYER;
}
