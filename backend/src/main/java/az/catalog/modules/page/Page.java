package az.catalog.modules.page;

import az.catalog.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A page identity. It holds no content — every word lives on a {@link PageVersion},
 * one per language per revision, which is what lets a draft be edited without touching
 * what the public site is currently serving.
 */
@Entity
@Table(name = "pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page extends SoftDeletableEntity {

    public enum PageType {
        STANDARD, HOME, CONTACT, ABOUT, CATALOG, BLOG, LEGAL, LANDING
    }

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_type", nullable = false, length = 32)
    @Builder.Default
    private PageType pageType = PageType.STANDARD;

    /**
     * System pages (HOME, CONTACT, CATALOG…) may be edited but never deleted — the
     * storefront router resolves them by code.
     */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "layout", nullable = false, length = 32)
    @Builder.Default
    private String layout = "DEFAULT";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
