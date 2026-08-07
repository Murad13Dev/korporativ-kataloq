package az.catalog.modules.language;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Content languages available to the installation. The primary key is the ISO code itself
 * ("az", "en", "ru") which keeps every translation table readable in raw SQL.
 */
@Entity
@Table(name = "languages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Language {

    @Id
    @Column(name = "code", length = 8, nullable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** Endonym shown in the language switcher ("Azərbaycan", "English", "Русский"). */
    @Column(name = "native_name", length = 64)
    private String nativeName;

    @Column(name = "flag_code", length = 8)
    private String flagCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
