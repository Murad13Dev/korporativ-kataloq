package az.catalog.modules.catalog.product;

import az.catalog.modules.catalog.attribute.Attribute;
import az.catalog.modules.catalog.attribute.AttributeOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One specification value. Exactly one of the typed columns is populated, matching the
 * attribute's data type — enforced by {@code ck_pav_single_value} in the schema.
 */
@Entity
@Table(name = "product_attribute_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private Attribute attribute;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "option_id")
    private AttributeOption option;

    @Column(name = "text_value", columnDefinition = "text")
    private String textValue;

    @Column(name = "number_value", precision = 18, scale = 4)
    private BigDecimal numberValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Human-readable value for the specification table. */
    public String displayValue(String languageCode, String defaultLanguage) {
        if (option != null) {
            return option.label(languageCode, defaultLanguage);
        }
        if (numberValue != null) {
            return numberValue.stripTrailingZeros().toPlainString();
        }
        if (booleanValue != null) {
            return booleanValue ? "Bəli" : "Xeyr";
        }
        return textValue;
    }
}
