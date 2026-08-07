package az.catalog.modules.catalog.product;

import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.modules.language.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Frequently bought together" cross-sells. See {@link ProductBundle} for why this
 * carries no price of its own — {@link #forProduct} always re-sums the items' live
 * resolved prices rather than reading a stored total.
 */
@Service
@RequiredArgsConstructor
public class ProductBundleService {

    private final ProductBundleRepository repository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final LanguageService languageService;

    // =======================================================================
    // Storefront
    // =======================================================================

    @Transactional(readOnly = true)
    public ProductDtos.BundleDto forProduct(String slug, String language) {
        Product product = productRepository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Məhsul", slug));

        return repository.findFirstByPrimaryProductIdAndActiveTrueOrderBySortOrderAsc(product.getId())
                .map(bundle -> toDto(bundle, language))
                .orElse(null);
    }

    private ProductDtos.BundleDto toDto(ProductBundle bundle, String language) {
        List<Product> items = bundle.getItems().stream()
                .filter(Product::isPublished)
                .toList();
        List<ProductDtos.ProductCard> cards = productService.cardsFor(items, language);

        BigDecimal total = cards.stream()
                .map(card -> card.price().finalPrice())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = bundle.getPrimaryProduct().getCurrency();
        return new ProductDtos.BundleDto(bundle.getId(), bundle.getName(), cards, total, currency);
    }

    // =======================================================================
    // Admin
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<ProductDtos.BundleAdminDto> adminSearch(String search, Pageable pageable) {
        return repository.search(blankToNull(search), pageable).map(this::toAdminDto);
    }

    @Transactional
    public ProductDtos.BundleAdminDto create(ProductDtos.SaveBundleRequest request) {
        Product primary = productRepository.findAliveById(request.primaryProductId())
                .orElseThrow(() -> ApiException.notFound("Məhsul", request.primaryProductId()));

        ProductBundle bundle = ProductBundle.builder()
                .name(request.name())
                .primaryProduct(primary)
                .active(request.active() == null || request.active())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .items(resolveItems(request.itemProductIds(), primary.getId()))
                .build();

        return toAdminDto(repository.save(bundle));
    }

    @Transactional
    public ProductDtos.BundleAdminDto update(Long id, ProductDtos.SaveBundleRequest request) {
        ProductBundle bundle = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Bundle", id));
        Product primary = productRepository.findAliveById(request.primaryProductId())
                .orElseThrow(() -> ApiException.notFound("Məhsul", request.primaryProductId()));

        bundle.setName(request.name());
        bundle.setPrimaryProduct(primary);
        bundle.setActive(request.active() == null || request.active());
        if (request.sortOrder() != null) {
            bundle.setSortOrder(request.sortOrder());
        }
        bundle.setItems(resolveItems(request.itemProductIds(), primary.getId()));

        return toAdminDto(bundle);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private Set<Product> resolveItems(List<Long> itemProductIds, Long primaryProductId) {
        if (itemProductIds.contains(primaryProductId)) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "itemProductIds",
                    "Əsas məhsul öz bundle-ının içində ola bilməz");
        }
        List<Product> found = productRepository.findAllPublishedByIds(itemProductIds);
        if (found.size() != new java.util.HashSet<>(itemProductIds).size()) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "itemProductIds", "Məhsullardan biri tapılmadı");
        }
        return new LinkedHashSet<>(found);
    }

    private ProductDtos.BundleAdminDto toAdminDto(ProductBundle bundle) {
        String fallback = languageService.defaultLanguageCode();
        List<ProductDtos.BundleItemDto> items = bundle.getItems().stream()
                .map(item -> new ProductDtos.BundleItemDto(
                        item.getId(), nameOf(item, fallback), item.getSku()))
                .toList();

        return new ProductDtos.BundleAdminDto(
                bundle.getId(), bundle.getName(), bundle.getPrimaryProduct().getId(),
                nameOf(bundle.getPrimaryProduct(), fallback), items, bundle.isActive(), bundle.getSortOrder());
    }

    private String nameOf(Product product, String language) {
        var translation = product.translation(language, language);
        return translation != null ? translation.getName() : product.getSku();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
