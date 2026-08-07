package az.catalog.modules.navigation;

import az.catalog.common.domain.BaseEntity;
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
 * A navigation menu identity. Like pages, it holds no items — those live on a
 * {@link MenuVersion}, so a menu can be rearranged in draft without the header changing
 * under visitors mid-edit.
 */
@Entity
@Table(name = "menus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Menu extends BaseEntity {

    /** Where the storefront renders this menu. */
    public enum Location {
        HEADER, FOOTER_PRIMARY, FOOTER_SECONDARY, MOBILE, LEGAL;

        public String label() {
            return switch (this) {
                case HEADER -> "Əsas menyu (header)";
                case FOOTER_PRIMARY -> "Footer — birinci sütun";
                case FOOTER_SECONDARY -> "Footer — ikinci sütun";
                case MOBILE -> "Mobil menyu";
                case LEGAL -> "Hüquqi linklər";
            };
        }
    }

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "location", nullable = false, length = 32)
    private Location location;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
