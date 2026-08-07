package az.catalog.modules.blog;

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

@Entity
@Table(name = "post_translations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "short_description", columnDefinition = "text")
    private String shortDescription;

    /** Sanitised HTML from the rich-text editor. */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;
}
