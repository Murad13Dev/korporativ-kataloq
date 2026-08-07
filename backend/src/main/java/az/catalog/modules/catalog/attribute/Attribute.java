package az.catalog.modules.catalog.attribute;

import az.catalog.common.domain.BaseEntity;
import az.catalog.common.domain.ContentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "attributes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attribute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_group_id")
    private AttributeGroup group;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 24)
    private AttributeDataType dataType;

    @Column(name = "unit", length = 24)
    private String unit;

    /** Exposed as a facet in the storefront filter panel. */
    @Column(name = "filterable", nullable = false)
    private boolean filterable;

    @Column(name = "show_in_listing", nullable = false)
    private boolean showInListing;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @MapKey(name = "languageCode")
    @Builder.Default
    private Map<String, AttributeTranslation> translations = new LinkedHashMap<>();

    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<AttributeOption> options = new ArrayList<>();

    public boolean usesOptions() {
        return dataType == AttributeDataType.SELECT || dataType == AttributeDataType.MULTI_SELECT;
    }

    public String name(String languageCode, String defaultLanguage) {
        AttributeTranslation translation = translations.get(languageCode);
        if (translation == null) {
            translation = translations.get(defaultLanguage);
        }
        return translation != null ? translation.getName() : code;
    }

    public void putTranslation(String languageCode, AttributeTranslation translation) {
        translation.setAttribute(this);
        translation.setLanguageCode(languageCode);
        translations.put(languageCode, translation);
    }
}
