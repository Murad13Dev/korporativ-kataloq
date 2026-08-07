package az.catalog.modules.page;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * One block on a page.
 *
 * The shared columns cover what almost every section needs (a title, a subtitle, a
 * button); anything section-specific — how many cards, which category to pull from —
 * lives in {@code configuration}, validated per {@link SectionType} on write.
 */
@Entity
@Table(name = "page_sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageSection {

    public enum BackgroundStyle {
        DEFAULT, MUTED, DARK, ACCENT, IMAGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_version_id", nullable = false)
    private PageVersion pageVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 48)
    private SectionType sectionType;

    /** Layout choice within the type, e.g. HERO → SPLIT. */
    @Column(name = "variant", nullable = false, length = 32)
    @Builder.Default
    private String variant = "DEFAULT";

    @Column(name = "eyebrow", length = 120)
    private String eyebrow;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "subtitle", length = 500)
    private String subtitle;

    /** Sanitised HTML for text sections. */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "media_id")
    private Long mediaId;

    @Column(name = "background_media_id")
    private Long backgroundMediaId;

    @Column(name = "button_text", length = 120)
    private String buttonText;

    @Column(name = "button_url", length = 500)
    private String buttonUrl;

    @Column(name = "secondary_button_text", length = 120)
    private String secondaryButtonText;

    @Column(name = "secondary_button_url", length = 500)
    private String secondaryButtonUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_style", nullable = false, length = 24)
    @Builder.Default
    private BackgroundStyle backgroundStyle = BackgroundStyle.DEFAULT;

    @Type(JsonBinaryType.class)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Hiding a section keeps it editable but removes it from the public page. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Deep copy for draft creation and page duplication. */
    public PageSection copy() {
        return PageSection.builder()
                .sectionType(sectionType)
                .variant(variant)
                .eyebrow(eyebrow)
                .title(title)
                .subtitle(subtitle)
                .content(content)
                .mediaId(mediaId)
                .backgroundMediaId(backgroundMediaId)
                .buttonText(buttonText)
                .buttonUrl(buttonUrl)
                .secondaryButtonText(secondaryButtonText)
                .secondaryButtonUrl(secondaryButtonUrl)
                .backgroundStyle(backgroundStyle)
                .configuration(configuration == null ? null : configuration.deepCopy())
                .sortOrder(sortOrder)
                .active(active)
                .build();
    }
}
