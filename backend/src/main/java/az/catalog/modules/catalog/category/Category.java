package az.catalog.modules.catalog.category;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.domain.SoftDeletableEntity;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "image_media_id")
    private Long imageMediaId;

    @Column(name = "icon_media_id")
    private Long iconMediaId;

    @Column(name = "banner_media_id")
    private Long bannerMediaId;

    @Column(name = "icon", length = 64)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "show_in_menu", nullable = false)
    @Builder.Default
    private boolean showInMenu = true;

    /**
     * Slash-separated ancestor ids ending with this category's own id ("1/14/57").
     * Recomputed by CategoryService whenever the parent changes; lets a descendant
     * query be a single indexed prefix match.
     */
    @Column(name = "path", nullable = false, length = 500)
    @Builder.Default
    private String path = "";

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @MapKey(name = "languageCode")
    @Builder.Default
    private Map<String, CategoryTranslation> translations = new LinkedHashMap<>();

    public void putTranslation(String languageCode, CategoryTranslation translation) {
        translation.setCategory(this);
        translation.setLanguageCode(languageCode);
        translations.put(languageCode, translation);
    }

    /** Falls back to any available translation so the admin list never shows a blank row. */
    public CategoryTranslation translation(String languageCode, String defaultLanguage) {
        CategoryTranslation match = translations.get(languageCode);
        if (match != null) {
            return match;
        }
        CategoryTranslation fallback = translations.get(defaultLanguage);
        return fallback != null ? fallback : translations.values().stream().findFirst().orElse(null);
    }

    public boolean isRoot() {
        return parent == null;
    }
}
