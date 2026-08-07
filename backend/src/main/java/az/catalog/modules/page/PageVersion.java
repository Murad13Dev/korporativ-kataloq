package az.catalog.modules.page;

import az.catalog.common.domain.BaseEntity;
import az.catalog.common.domain.VersionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One revision of a page in one language.
 *
 * Sections hang off the version rather than off the page, which is the whole point of the
 * design: editing a draft's sections cannot disturb what visitors are reading right now.
 */
@Entity
@Table(name = "page_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "cover_media_id")
    private Long coverMediaId;

    // --- SEO --------------------------------------------------------------

    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    @Column(name = "seo_keywords", length = 500)
    private String seoKeywords;

    @Column(name = "og_media_id")
    private Long ogMediaId;

    @Column(name = "canonical_url", length = 500)
    private String canonicalUrl;

    @Column(name = "allow_indexing", nullable = false)
    @Builder.Default
    private boolean allowIndexing = true;

    // --- state ------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @OneToMany(mappedBy = "pageVersion", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<PageSection> sections = new ArrayList<>();

    public void addSection(PageSection section) {
        section.setPageVersion(this);
        sections.add(section);
    }

    public boolean isPublished() {
        return status == VersionStatus.PUBLISHED;
    }
}
