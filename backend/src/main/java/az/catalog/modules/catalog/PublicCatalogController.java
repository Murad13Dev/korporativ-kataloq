package az.catalog.modules.catalog;

import az.catalog.common.config.RequestLanguage;
import az.catalog.common.dto.ApiResponses.DataResponse;
import az.catalog.common.dto.ApiResponses.PageResponse;
import az.catalog.modules.catalog.category.CategoryDtos;
import az.catalog.modules.catalog.category.CategoryService;
import az.catalog.modules.catalog.product.ProductBundleService;
import az.catalog.modules.catalog.product.ProductDtos;
import az.catalog.modules.catalog.product.ProductFilter;
import az.catalog.modules.catalog.product.PriceWatchService;
import az.catalog.modules.catalog.product.ProductPresenceService;
import az.catalog.modules.catalog.product.ProductService;
import az.catalog.common.domain.ContentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Public · Kataloq")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
public class PublicCatalogController {

    private final ProductService productService;
    private final ProductBundleService bundleService;
    private final CategoryService categoryService;
    private final ProductPresenceService presenceService;
    private final PriceWatchService priceWatchService;

    // --- categories ---------------------------------------------------------

    @Operation(summary = "Aktiv kateqoriyalar")
    @GetMapping("/categories")
    public DataResponse<List<CategoryDtos.CategoryCard>> categories(@RequestLanguage String language) {
        return DataResponse.of(categoryService.publishedList(language));
    }

    @Operation(summary = "Kateqoriya ağacı")
    @GetMapping("/categories/tree")
    public DataResponse<List<CategoryDtos.CategoryNode>> categoryTree(@RequestLanguage String language) {
        return DataResponse.of(categoryService.publicTree(language));
    }

    @Operation(summary = "Seçilmiş kateqoriyalar")
    @GetMapping("/categories/featured")
    public DataResponse<List<CategoryDtos.CategoryCard>> featuredCategories(
            @RequestLanguage String language,
            @RequestParam(defaultValue = "6") int limit) {
        return DataResponse.of(categoryService.featured(language, Math.min(limit, 24)));
    }

    @Operation(summary = "Kateqoriya detalı")
    @GetMapping("/categories/{slug}")
    public DataResponse<CategoryDtos.CategoryCard> category(@PathVariable String slug,
                                                            @RequestLanguage String language) {
        return DataResponse.of(categoryService.publishedBySlug(slug, language));
    }

    @Operation(summary = "Kateqoriya breadcrumb-ı")
    @GetMapping("/categories/{slug}/breadcrumb")
    public DataResponse<List<CategoryDtos.CategoryCard>> breadcrumb(@PathVariable String slug,
                                                                    @RequestLanguage String language) {
        return DataResponse.of(categoryService.breadcrumb(slug, language));
    }

    @Operation(summary = "Kateqoriya məhsulları")
    @GetMapping("/categories/{slug}/products")
    public PageResponse<ProductDtos.ProductCard> categoryProducts(
            @PathVariable String slug,
            @RequestLanguage String language,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean discounted,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<Long> attributeOptionIds,
            @PageableDefault(size = 24) Pageable pageable) {

        ProductFilter filter = new ProductFilter(search, null, slug, null, null, discounted,
                inStock, null, minPrice, maxPrice, attributeOptionIds, null, ContentStatus.ACTIVE);
        return PageResponse.of(productService.search(filter, language, pageable));
    }

    // --- products -----------------------------------------------------------

    @Operation(summary = "Məhsul siyahısı",
            description = """
                    Filtr nümunəsi:
                    `?search=kreslo&categorySlug=mebel&discounted=true&minPrice=50&maxPrice=500&sort=createdAt,desc&page=0&size=24`

                    **Qeyd:** `minPrice`/`maxPrice` və qiymətə görə sıralama *kataloq* (endirimsiz) qiymətinə görə işləyir.
                    """)
    @GetMapping("/products")
    public PageResponse<ProductDtos.ProductCard> products(
            @RequestLanguage String language,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false, name = "new") Boolean isNew,
            @Parameter(description = "Yalnız endirimdə olan məhsullar")
            @RequestParam(required = false) Boolean discounted,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Attribut seçim id-ləri (facet filtri)")
            @RequestParam(required = false) List<Long> attributeOptionIds,
            @PageableDefault(size = 24, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable) {

        ProductFilter filter = new ProductFilter(search, categoryId, categorySlug, featured, isNew,
                discounted, inStock, null, minPrice, maxPrice, attributeOptionIds, null, ContentStatus.ACTIVE);
        return PageResponse.of(productService.search(filter, language, pageable));
    }

    @Operation(summary = "Məhsul detalı")
    @GetMapping("/products/{slug}")
    public DataResponse<ProductDtos.ProductDetail> product(@PathVariable String slug,
                                                           @RequestLanguage String language) {
        ProductDtos.ProductDetail detail = productService.detailBySlug(slug, language);
        productService.recordView(detail.id());
        return DataResponse.of(detail);
    }

    @Operation(summary = "Seçilmiş məhsullar")
    @GetMapping("/products/featured")
    public DataResponse<List<ProductDtos.ProductCard>> featured(
            @RequestLanguage String language,
            @RequestParam(defaultValue = "8") int limit) {
        return DataResponse.of(productService.featured(language, Math.min(limit, 48)));
    }

    @Operation(summary = "Endirimdə olan məhsullar",
            description = "Ən böyük endirim faizi əvvəldə olmaqla sıralanır.")
    @GetMapping("/products/discounted")
    public DataResponse<List<ProductDtos.ProductCard>> discounted(
            @RequestLanguage String language,
            @RequestParam(defaultValue = "8") int limit) {
        return DataResponse.of(productService.discounted(language, Math.min(limit, 48)));
    }

    @Operation(summary = "Birlikdə alınan məhsullar",
            description = "Admin tərəfindən qurulmuş 'birlikdə al' siyahısı; yoxdursa boş cavab qayıdır.")
    @GetMapping("/products/{slug}/bundle")
    public DataResponse<ProductDtos.BundleDto> bundle(@PathVariable String slug, @RequestLanguage String language) {
        return DataResponse.of(bundleService.forProduct(slug, language));
    }

    @Operation(summary = "Stok bildirişinə abunə olmaq",
            description = "Məhsul yenidən stoka gələndə bu e-poçta bir dəfə bildiriş göndərilir.")
    @PostMapping("/products/{slug}/notify-stock")
    public ResponseEntity<Void> notifyStock(
            @PathVariable String slug, @Valid @RequestBody ProductDtos.StockNotificationRequest request) {
        productService.subscribeStockNotification(slug, request.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Id siyahısına görə məhsullar",
            description = "Göndərilən sıra qorunur — \"son baxılanlar\" kimi client-side siyahılar üçün.")
    @GetMapping("/products/by-ids")
    public DataResponse<List<ProductDtos.ProductCard>> byIds(
            @RequestLanguage String language,
            @RequestParam List<Long> ids) {
        List<Long> limited = ids.size() > 24 ? ids.subList(0, 24) : ids;
        return DataResponse.of(productService.byIds(limited, language));
    }

    @Operation(summary = "Əlaqəli məhsullar")
    @GetMapping("/products/{slug}/related")
    public DataResponse<List<ProductDtos.ProductCard>> related(
            @PathVariable String slug,
            @RequestLanguage String language,
            @RequestParam(defaultValue = "4") int limit) {
        return DataResponse.of(productService.related(slug, language, Math.min(limit, 24)));
    }

    @Operation(summary = "Bunu alanlar bunu da aldı",
            description = "Sifariş tarixçəsindən hesablanan real birgə-alış statistikası.")
    @GetMapping("/products/{slug}/also-bought")
    public DataResponse<List<ProductDtos.ProductCard>> alsoBought(
            @PathVariable String slug,
            @RequestLanguage String language,
            @RequestParam(defaultValue = "4") int limit) {
        return DataResponse.of(productService.alsoBought(slug, language, Math.min(limit, 24)));
    }

    @Operation(summary = "Qiymət düşdükdə xəbər ver",
            description = "Bu məhsula istənilən kampaniya tətbiq olunanda bu e-poçta bir dəfə bildiriş göndərilir.")
    @PostMapping("/products/{slug}/price-watch")
    public ResponseEntity<Void> priceWatch(
            @PathVariable String slug, @Valid @RequestBody ProductDtos.StockNotificationRequest request) {
        priceWatchService.subscribe(slug, request.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Canlı baxış siqnalı",
            description = """
                    Məhsul səhifəsi açıq olduqca dövri çağırılır. Cavabda dərhal cari sayı qaytarır;
                    sonrakı dəyişikliklər `/topic/product/presence/{id}` STOMP kanalından gəlir.
                    """)
    @PostMapping("/products/{id}/presence")
    public DataResponse<Integer> presence(
            @PathVariable Long id,
            @RequestHeader("X-Visitor-Token") String visitorToken) {
        return DataResponse.of(presenceService.heartbeat(id, visitorToken));
    }
}
