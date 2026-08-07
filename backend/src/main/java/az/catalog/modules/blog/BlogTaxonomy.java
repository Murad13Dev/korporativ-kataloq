package az.catalog.modules.blog;

import az.catalog.common.domain.BaseEntity;
import az.catalog.common.domain.ContentStatus;
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

/**
 * Blog taxonomy: categories and tags.
 *
 * Grouped in one file because they are the same shape — a slug, a status, and a
 * translated name — and separating them would suggest a distinction that does not exist.
 * {@link Post} keeps its own file; it is the actual aggregate.
 */
public final class BlogTaxonomy {

    private BlogTaxonomy() {
    }

    // -----------------------------------------------------------------------
    // Category — a post belongs to at most one
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "post_categories")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostCategory extends BaseEntity {

        @Column(name = "slug", nullable = false, length = 180)
        private String slug;

        /** Optional accent used on the category chip. */
        @Column(name = "color", length = 16)
        private String color;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 16)
        @Builder.Default
        private ContentStatus status = ContentStatus.ACTIVE;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true,
                fetch = FetchType.LAZY)
        @MapKey(name = "languageCode")
        @Builder.Default
        private Map<String, PostCategoryTranslation> translations = new LinkedHashMap<>();

        public String name(String languageCode, String defaultLanguage) {
            var translation = translations.get(languageCode);
            if (translation == null) {
                translation = translations.get(defaultLanguage);
            }
            return translation != null ? translation.getName() : slug;
        }

        public void putTranslation(String languageCode, PostCategoryTranslation translation) {
            translation.setCategory(this);
            translation.setLanguageCode(languageCode);
            translations.put(languageCode, translation);
        }
    }

    @Entity
    @Table(name = "post_category_translations")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostCategoryTranslation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "category_id", nullable = false)
        private PostCategory category;

        @Column(name = "language_code", nullable = false, length = 8)
        private String languageCode;

        @Column(name = "name", nullable = false, length = 160)
        private String name;

        @Column(name = "description", length = 500)
        private String description;
    }

    // -----------------------------------------------------------------------
    // Tag — many per post
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "tags")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Tag {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        /** Stable machine key, unique across languages. */
        @Column(name = "code", nullable = false, length = 64)
        private String code;

        @Column(name = "slug", nullable = false, length = 180)
        private String slug;

        @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, orphanRemoval = true,
                fetch = FetchType.LAZY)
        @MapKey(name = "languageCode")
        @Builder.Default
        private Map<String, TagTranslation> translations = new LinkedHashMap<>();

        public String name(String languageCode, String defaultLanguage) {
            var translation = translations.get(languageCode);
            if (translation == null) {
                translation = translations.get(defaultLanguage);
            }
            return translation != null ? translation.getName() : code;
        }

        public void putTranslation(String languageCode, TagTranslation translation) {
            translation.setTag(this);
            translation.setLanguageCode(languageCode);
            translations.put(languageCode, translation);
        }
    }

    @Entity
    @Table(name = "tag_translations")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TagTranslation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "tag_id", nullable = false)
        private Tag tag;

        @Column(name = "language_code", nullable = false, length = 8)
        private String languageCode;

        @Column(name = "name", nullable = false, length = 120)
        private String name;
    }
}
