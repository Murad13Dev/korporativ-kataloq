package az.catalog.modules.blog;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.domain.SoftDeletableEntity;
import az.catalog.modules.blog.BlogTaxonomy.PostCategory;
import az.catalog.modules.blog.BlogTaxonomy.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A blog post.
 *
 * Unlike pages, posts are not versioned: an article is written once and corrected in
 * place. Draft/published is a single status, which is what an editorial workflow
 * actually needs — nobody asks to roll a blog post back to last Tuesday.
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private PostCategory category;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "cover_media_id")
    private Long coverMediaId;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    /** Estimated from the body on save; the editor may override it. */
    @Column(name = "reading_minutes")
    private Integer readingMinutes;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    /**
     * When the post goes live. A future value keeps an ACTIVE post hidden until then,
     * which is how scheduling works without a separate flag.
     */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @MapKey(name = "languageCode")
    @Builder.Default
    private Map<String, PostTranslation> translations = new LinkedHashMap<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @Builder.Default
    private Set<Tag> tags = new LinkedHashSet<>();

    public void putTranslation(String languageCode, PostTranslation translation) {
        translation.setPost(this);
        translation.setLanguageCode(languageCode);
        translations.put(languageCode, translation);
    }

    public PostTranslation translation(String languageCode, String defaultLanguage) {
        PostTranslation match = translations.get(languageCode);
        if (match != null) {
            return match;
        }
        PostTranslation fallback = translations.get(defaultLanguage);
        return fallback != null ? fallback : translations.values().stream().findFirst().orElse(null);
    }

    /** Live means: active, not deleted, and its publish moment has passed. */
    public boolean isLive(OffsetDateTime at) {
        return status == ContentStatus.ACTIVE
                && !isDeleted()
                && publishedAt != null
                && !publishedAt.isAfter(at);
    }

    public boolean isScheduled(OffsetDateTime at) {
        return status == ContentStatus.ACTIVE
                && publishedAt != null
                && publishedAt.isAfter(at);
    }
}
