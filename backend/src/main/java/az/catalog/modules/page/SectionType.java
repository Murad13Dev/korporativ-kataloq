package az.catalog.modules.page;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The section registry.
 *
 * This enum is the contract between the admin editor and the public renderer, and it is
 * deliberately a closed set. As the plan's Risk 1 states, this is <em>not</em> a free
 * page builder: an admin adds, removes, reorders, hides and edits sections drawn from
 * this list, and picks a layout variant — they never place arbitrary elements. That is
 * what makes it possible to guarantee the design stays responsive and on-brand.
 *
 * Each entry declares which of the shared columns it actually uses, so the editor can
 * render only the relevant inputs instead of a wall of mostly-empty fields.
 */
public enum SectionType {

    // --- headers & hero ---------------------------------------------------
    HERO("Hero başlıq", "Səhifənin əsas banneri",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.MEDIA, Field.BACKGROUND_MEDIA,
                    Field.BUTTON, Field.SECONDARY_BUTTON),
            List.of("DEFAULT", "SPLIT", "CENTERED", "FULL_WIDTH")),

    PAGE_HEADER("Səhifə başlığı", "Sadə başlıq və təsvir",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.MEDIA),
            List.of("DEFAULT", "CENTERED", "WITH_IMAGE")),

    // --- text -------------------------------------------------------------
    RICH_TEXT("Mətn bloku", "Sərbəst formatlanmış mətn",
            Set.of(Field.TITLE, Field.CONTENT),
            List.of("DEFAULT", "NARROW", "TWO_COLUMN")),

    TEXT_WITH_IMAGE("Mətn və şəkil", "Yan-yana mətn və şəkil",
            Set.of(Field.EYEBROW, Field.TITLE, Field.CONTENT, Field.MEDIA, Field.BUTTON),
            List.of("IMAGE_RIGHT", "IMAGE_LEFT")),

    // --- catalog ----------------------------------------------------------
    FEATURED_CATEGORIES("Seçilmiş kateqoriyalar", "Kateqoriya kartları",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.BUTTON),
            List.of("GRID", "CAROUSEL", "COMPACT")),

    FEATURED_PRODUCTS("Seçilmiş məhsullar", "Featured işarəli məhsullar",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.BUTTON),
            List.of("GRID", "CAROUSEL")),

    /** Reads the live campaign list; shows nothing when no campaign is running. */
    DISCOUNTED_PRODUCTS("Endirimdə olan məhsullar", "Aktiv kampaniyadakı məhsullar",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.BUTTON),
            List.of("GRID", "CAROUSEL", "BANNER")),

    PRODUCT_LIST("Məhsul siyahısı", "Seçilmiş kateqoriyanın məhsulları",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.BUTTON),
            List.of("GRID", "CAROUSEL")),

    // --- social proof -----------------------------------------------------
    BENEFITS("Üstünlüklər", "İkonlu üstünlük kartları",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE),
            List.of("ICON_GRID", "ICON_LIST", "CARDS", "STRIP")),

    STATISTICS("Statistika", "Rəqəmlərlə göstəricilər",
            Set.of(Field.TITLE, Field.SUBTITLE),
            List.of("COUNTER", "CARDS")),

    TESTIMONIALS("Müştəri rəyləri", "Rəy sliderı",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE),
            List.of("SLIDER", "GRID", "SINGLE")),

    PARTNERS("Partnyorlar", "Loqo zolağı",
            Set.of(Field.TITLE, Field.SUBTITLE),
            List.of("LOGO_STRIP", "GRID")),

    // --- content ----------------------------------------------------------
    LATEST_POSTS("Son bloq yazıları", "Bloqdan son məqalələr",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE, Field.BUTTON),
            List.of("GRID", "LIST")),

    FAQ("Tez-tez verilən suallar", "Açılıb-bağlanan suallar",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE),
            List.of("ACCORDION", "TWO_COLUMN")),

    GALLERY("Qalereya", "Şəkil qalereyası",
            Set.of(Field.TITLE, Field.SUBTITLE),
            List.of("GRID", "MASONRY", "CAROUSEL")),

    TIMELINE("Zaman xətti", "Tarixi mərhələlər",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE),
            List.of("HORIZONTAL", "VERTICAL")),

    TEAM("Komanda", "Əməkdaş kartları",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE),
            List.of("GRID", "CAROUSEL")),

    // --- conversion -------------------------------------------------------
    CTA_BANNER("Çağırış banneri", "Diqqət çəkən çağırış bloku",
            Set.of(Field.TITLE, Field.SUBTITLE, Field.BUTTON, Field.SECONDARY_BUTTON,
                    Field.BACKGROUND_MEDIA),
            List.of("CENTERED", "SPLIT", "COMPACT")),

    CONTACT_FORM("Əlaqə forması", "Əlaqə forması və məlumatlar",
            Set.of(Field.EYEBROW, Field.TITLE, Field.SUBTITLE),
            List.of("WITH_INFO", "SIMPLE", "WITH_MAP")),

    NEWSLETTER("Xəbər bülleteni", "Abunə forması",
            Set.of(Field.TITLE, Field.SUBTITLE),
            List.of("INLINE", "CENTERED")),

    MAP("Xəritə", "Ünvan xəritəsi",
            Set.of(Field.TITLE, Field.SUBTITLE),
            List.of("FULL_WIDTH", "BOXED")),

    // --- structural -------------------------------------------------------
    SPACER("Boşluq", "Bölmələr arası aralıq",
            Set.of(),
            List.of("SMALL", "MEDIUM", "LARGE")),

    HTML_EMBED("HTML blok", "Təmizlənmiş HTML məzmun",
            Set.of(Field.TITLE, Field.CONTENT),
            List.of("DEFAULT", "NARROW"));

    /** Shared columns on {@code page_sections} that a section may use. */
    public enum Field {
        EYEBROW, TITLE, SUBTITLE, CONTENT, MEDIA, BACKGROUND_MEDIA, BUTTON, SECONDARY_BUTTON
    }

    private final String label;
    private final String description;
    private final Set<Field> fields;
    private final List<String> variants;

    SectionType(String label, String description, Set<Field> fields, List<String> variants) {
        this.label = label;
        this.description = description;
        this.fields = fields;
        this.variants = variants;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Set<Field> fields() {
        return fields;
    }

    public List<String> variants() {
        return variants;
    }

    public boolean uses(Field field) {
        return fields.contains(field);
    }

    public String defaultVariant() {
        return variants.isEmpty() ? "DEFAULT" : variants.get(0);
    }

    /** Falls back to the first declared variant when an unknown one is submitted. */
    public String resolveVariant(String requested) {
        return requested != null && variants.contains(requested) ? requested : defaultVariant();
    }

    public static Optional<SectionType> parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
