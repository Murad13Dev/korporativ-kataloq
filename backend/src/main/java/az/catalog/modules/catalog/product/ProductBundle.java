package az.catalog.modules.catalog.product;

import az.catalog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A curated "frequently bought together" cross-sell shown on {@code primaryProduct}'s
 * page. Deliberately carries no price of its own — see the migration comment; the
 * combined price shown to a shopper is always the live sum of the items' own resolved
 * prices, computed at read time, never stored here.
 */
@Entity
@Table(name = "product_bundles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBundle extends BaseEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_product_id", nullable = false)
    private Product primaryProduct;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_bundle_items",
            joinColumns = @JoinColumn(name = "bundle_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    private Set<Product> items = new LinkedHashSet<>();
}
