package az.catalog.modules.navigation;

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

@Entity
@Table(name = "menu_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /**
     * Every item of the version, flat. The parent/child hierarchy is reconstructed in the
     * service — loading a self-referencing tree through JPA produces one query per level.
     */
    @OneToMany(mappedBy = "menuVersion", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<MenuItem> items = new ArrayList<>();

    public void addItem(MenuItem item) {
        item.setMenuVersion(this);
        items.add(item);
    }
}
