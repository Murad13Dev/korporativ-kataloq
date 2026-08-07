package az.catalog.modules.navigation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    /**
     * What the item points at. Anything other than CUSTOM/EXTERNAL stores a
     * {@code referenceId} so the URL can be regenerated if the target's slug changes.
     */
    public enum LinkType {
        CUSTOM, PAGE, CATEGORY, PRODUCT, POST, EXTERNAL;

        public String label() {
            return switch (this) {
                case CUSTOM -> "Sərbəst link";
                case PAGE -> "Səhifə";
                case CATEGORY -> "Kateqoriya";
                case PRODUCT -> "Məhsul";
                case POST -> "Bloq yazısı";
                case EXTERNAL -> "Xarici link";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_version_id", nullable = false)
    private MenuVersion menuVersion;

    /** Self-reference for sub-menus. Only one level deep is rendered by the storefront. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MenuItem parent;

    /** Fallback label, used when no translation exists for the requested language. */
    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 24)
    @Builder.Default
    private LinkType linkType = LinkType.CUSTOM;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "target", nullable = false, length = 16)
    @Builder.Default
    private String target = "_self";

    /** Renders the item as a button rather than a plain link. */
    @Column(name = "highlight", nullable = false)
    private boolean highlight;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @MapKey(name = "languageCode")
    @Builder.Default
    private Map<String, MenuItemTranslation> translations = new LinkedHashMap<>();

    public void putTranslation(String languageCode, MenuItemTranslation translation) {
        translation.setMenuItem(this);
        translation.setLanguageCode(languageCode);
        translations.put(languageCode, translation);
    }

    public String title(String languageCode, String defaultLanguage) {
        MenuItemTranslation translation = translations.get(languageCode);
        if (translation == null) {
            translation = translations.get(defaultLanguage);
        }
        return translation != null && translation.getTitle() != null ? translation.getTitle() : title;
    }

    public String description(String languageCode, String defaultLanguage) {
        MenuItemTranslation translation = translations.get(languageCode);
        if (translation == null) {
            translation = translations.get(defaultLanguage);
        }
        return translation != null && translation.getDescription() != null
                ? translation.getDescription() : description;
    }

    /** Copy without the parent link; the caller rewires the hierarchy after cloning. */
    public MenuItem copyShallow() {
        MenuItem copy = MenuItem.builder()
                .title(title)
                .url(url)
                .linkType(linkType)
                .referenceId(referenceId)
                .icon(icon)
                .description(description)
                .target(target)
                .highlight(highlight)
                .sortOrder(sortOrder)
                .active(active)
                .build();
        translations.forEach((code, translation) -> copy.putTranslation(code,
                MenuItemTranslation.builder()
                        .title(translation.getTitle())
                        .description(translation.getDescription())
                        .build()));
        return copy;
    }
}
