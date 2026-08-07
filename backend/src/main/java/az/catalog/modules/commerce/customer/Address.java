package az.catalog.modules.commerce.customer;

import az.catalog.common.domain.BaseEntity;
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

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "title", nullable = false, length = 80)
    @Builder.Default
    private String title = "Ev";

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "country", nullable = false, length = 80)
    @Builder.Default
    private String country = "Azərbaycan";

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "district", length = 120)
    private String district;

    @Column(name = "address_line", nullable = false, columnDefinition = "text")
    private String addressLine;

    @Column(name = "postal_code", length = 24)
    private String postalCode;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
