package az.catalog.modules.content;

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
 * The small corporate content blocks that page sections draw on: benefits, statistics,
 * testimonials, partners and FAQ entries.
 *
 * Grouped into one file deliberately. Each is a handful of columns with the same shape —
 * status, sort order, translations — and five near-identical files would obscure that
 * they are variations on one theme rather than five independent concepts.
 */
public final class ContentEntities {

    private ContentEntities() {
    }

    // -----------------------------------------------------------------------
    // Benefit — "why choose us" cards
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "benefits")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Benefit extends BaseEntity {

        /** Lucide icon name, rendered client-side. */
        @Column(name = "icon", length = 64)
        private String icon;

        @Column(name = "icon_media_id")
        private Long iconMediaId;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 16)
        @Builder.Default
        private ContentStatus status = ContentStatus.ACTIVE;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        @OneToMany(mappedBy = "benefit", cascade = CascadeType.ALL, orphanRemoval = true,
                fetch = FetchType.LAZY)
        @MapKey(name = "languageCode")
        @Builder.Default
        private Map<String, BenefitTranslation> translations = new LinkedHashMap<>();
    }

    @Entity
    @Table(name = "benefit_translations")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BenefitTranslation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "benefit_id", nullable = false)
        private Benefit benefit;

        @Column(name = "language_code", nullable = false, length = 8)
        private String languageCode;

        @Column(name = "title", nullable = false, length = 160)
        private String title;

        @Column(name = "description", length = 500)
        private String description;
    }

    // -----------------------------------------------------------------------
    // Statistic — counter tiles
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "statistics")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Statistic extends BaseEntity {

        @Column(name = "icon", length = 64)
        private String icon;

        /** Stored as text: "2 500", "4.8", "99.9" all need to render verbatim. */
        @Column(name = "value", nullable = false, length = 48)
        private String value;

        @Column(name = "suffix", length = 16)
        private String suffix;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 16)
        @Builder.Default
        private ContentStatus status = ContentStatus.ACTIVE;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        @OneToMany(mappedBy = "statistic", cascade = CascadeType.ALL, orphanRemoval = true,
                fetch = FetchType.LAZY)
        @MapKey(name = "languageCode")
        @Builder.Default
        private Map<String, StatisticTranslation> translations = new LinkedHashMap<>();
    }

    @Entity
    @Table(name = "statistic_translations")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatisticTranslation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "statistic_id", nullable = false)
        private Statistic statistic;

        @Column(name = "language_code", nullable = false, length = 8)
        private String languageCode;

        @Column(name = "label", nullable = false, length = 160)
        private String label;
    }

    // -----------------------------------------------------------------------
    // Testimonial
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "testimonials")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Testimonial extends BaseEntity {

        @Column(name = "person_name", nullable = false, length = 160)
        private String personName;

        @Column(name = "position", length = 160)
        private String position;

        @Column(name = "company_name", length = 160)
        private String companyName;

        @Column(name = "photo_media_id")
        private Long photoMediaId;

        @Column(name = "rating")
        private Integer rating;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 16)
        @Builder.Default
        private ContentStatus status = ContentStatus.ACTIVE;

        @Column(name = "featured", nullable = false)
        private boolean featured;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        @OneToMany(mappedBy = "testimonial", cascade = CascadeType.ALL, orphanRemoval = true,
                fetch = FetchType.LAZY)
        @MapKey(name = "languageCode")
        @Builder.Default
        private Map<String, TestimonialTranslation> translations = new LinkedHashMap<>();
    }

    @Entity
    @Table(name = "testimonial_translations")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TestimonialTranslation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "testimonial_id", nullable = false)
        private Testimonial testimonial;

        @Column(name = "language_code", nullable = false, length = 8)
        private String languageCode;

        @Column(name = "content", nullable = false, columnDefinition = "text")
        private String content;
    }

    // -----------------------------------------------------------------------
    // Partner — logo strip. No translations: a company name is a proper noun.
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "partners")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Partner extends BaseEntity {

        @Column(name = "name", nullable = false, length = 160)
        private String name;

        @Column(name = "logo_media_id")
        private Long logoMediaId;

        @Column(name = "website_url", length = 500)
        private String websiteUrl;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 16)
        @Builder.Default
        private ContentStatus status = ContentStatus.ACTIVE;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;
    }

    // -----------------------------------------------------------------------
    // FAQ
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "faqs")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Faq extends BaseEntity {

        /** Optional scope: a FAQ tied to a page shows only there. */
        @Column(name = "page_id")
        private Long pageId;

        @Column(name = "category", length = 64)
        private String category;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 16)
        @Builder.Default
        private ContentStatus status = ContentStatus.ACTIVE;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        @OneToMany(mappedBy = "faq", cascade = CascadeType.ALL, orphanRemoval = true,
                fetch = FetchType.LAZY)
        @MapKey(name = "languageCode")
        @Builder.Default
        private Map<String, FaqTranslation> translations = new LinkedHashMap<>();
    }

    @Entity
    @Table(name = "faq_translations")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FaqTranslation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "faq_id", nullable = false)
        private Faq faq;

        @Column(name = "language_code", nullable = false, length = 8)
        private String languageCode;

        @Column(name = "question", nullable = false, columnDefinition = "text")
        private String question;

        @Column(name = "answer", nullable = false, columnDefinition = "text")
        private String answer;
    }
}
