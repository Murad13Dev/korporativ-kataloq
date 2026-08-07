package az.catalog.modules.catalog.attribute;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "attribute_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private Attribute attribute;

    /** Stable machine value; the human label lives in the translations. */
    @Column(name = "value", nullable = false, length = 160)
    private String value;

    /** Swatch colour for attributes rendered as chips (e.g. "Rəng"). */
    @Column(name = "color_hex", length = 16)
    private String colorHex;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @MapKey(name = "languageCode")
    @Builder.Default
    private Map<String, AttributeOptionTranslation> translations = new LinkedHashMap<>();

    public String label(String languageCode, String defaultLanguage) {
        AttributeOptionTranslation translation = translations.get(languageCode);
        if (translation == null) {
            translation = translations.get(defaultLanguage);
        }
        return translation != null ? translation.getLabel() : value;
    }

    public void putTranslation(String languageCode, AttributeOptionTranslation translation) {
        translation.setOption(this);
        translation.setLanguageCode(languageCode);
        translations.put(languageCode, translation);
    }
}
