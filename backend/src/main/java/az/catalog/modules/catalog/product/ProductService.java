package az.catalog.modules.catalog.product;

import az.catalog.common.domain.ContentStatus;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import az.catalog.common.util.HtmlSanitizer;
import az.catalog.common.util.SlugUtil;
import az.catalog.modules.catalog.attribute.Attribute;
import az.catalog.modules.catalog.attribute.AttributeDataType;
import az.catalog.modules.catalog.attribute.AttributeOption;
import az.catalog.modules.catalog.attribute.AttributeOptionRepository;
import az.catalog.modules.catalog.attribute.AttributeRepository;
import az.catalog.modules.catalog.category.Category;
import az.catalog.modules.catalog.category.CategoryRepository;
import az.catalog.modules.catalog.discount.DiscountResolver;
import az.catalog.modules.catalog.discount.PriceInfo;
import az.catalog.modules.catalog.review.ProductReviewRepository;
import az.catalog.common.config.AppProperties;
import az.catalog.modules.notification.MailService;
import az.catalog.modules.language.LanguageService;
import az.catalog.modules.media.MediaFile;
import az.catalog.modules.media.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final ProductVariantRepository variantRepository;
    private final ProductReviewRepository reviewRepository;
    private final StockNotificationRepository stockNotificationRepository;
    private final MailService mailService;
    private final AppProperties properties;
    private final CategoryRepository categoryRepository;
    private final AttributeRepository attributeRepository;
    private final AttributeOptionRepository attributeOptionRepository;
    private final DiscountResolver discountResolver;
    private final LanguageService languageService;
    private final MediaService mediaService;
    private final HtmlSanitizer sanitizer;

    // =======================================================================
    // Storefront reads
    // =======================================================================

    /**
     * Paged catalog list. Campaigns are resolved once for the whole page rather than
     * per product, so a 24-item grid costs one extra query, not 24.
     */
    @Transactional(readOnly = true)
    public Page<ProductDtos.ProductCard> search(ProductFilter filter, String language, Pageable pageable) {
        Collection<Long> categoryIds = resolveCategoryIds(filter);
        List<Long> discountedIds = Boolean.TRUE.equals(filter.discounted())
                ? discountResolver.discountedProductIds()
                : List.of();

        Specification<Product> specification =
                ProductSpecifications.build(filter, language, categoryIds, discountedIds);

        // Facet filtering needs one EXISTS per attribute, which requires knowing which
        // attribute each selected option belongs to.
        if (filter.attributeOptionIds() != null && !filter.attributeOptionIds().isEmpty()) {
            Map<Long, List<Long>> byAttribute = attributeOptionRepository
                    .findAllDetailByIds(filter.attributeOptionIds()).stream()
                    .collect(Collectors.groupingBy(
                            option -> option.getAttribute().getId(),
                            Collectors.mapping(AttributeOption::getId, Collectors.toList())));
            specification = specification == null
                    ? ProductSpecifications.matchesFacets(byAttribute)
                    : specification.and(ProductSpecifications.matchesFacets(byAttribute));
        }

        Page<Product> page = repository.findAll(specification, pageable);
        return mapCards(page, language);
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductDetail detailBySlug(String slug, String language) {
        Product product = repository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Məhsul", slug));
        return toDetail(product, language);
    }

    @Transactional
    public void recordView(Long productId) {
        repository.incrementViewCount(productId);
    }

    /** "Tell me when it's back" — idempotent, a repeat sign-up is simply a no-op. */
    @Transactional
    public void subscribeStockNotification(String slug, String email) {
        Product product = repository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Məhsul", slug));
        String normalised = email.trim().toLowerCase();
        if (stockNotificationRepository.existsByProductIdAndEmailIgnoreCase(product.getId(), normalised)) {
            return;
        }
        stockNotificationRepository.save(StockNotification.builder()
                .product(product)
                .email(normalised)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    /** Mails every pending subscriber once, then marks them handled so a later restock re-arms them. */
    private void notifyBackInStock(Product product) {
        List<StockNotification> pending = stockNotificationRepository
                .findByProductIdAndNotifiedAtIsNull(product.getId());
        if (pending.isEmpty()) {
            return;
        }
        String fallback = languageService.defaultLanguageCode();
        var translation = product.translation(fallback, fallback);
        String name = translation != null ? translation.getName() : product.getSku();
        String link = properties.getPublicUrl() + "/mehsul/" + product.getSlug();

        OffsetDateTime now = OffsetDateTime.now();
        for (StockNotification notification : pending) {
            mailService.sendBackInStock(notification.getEmail(), name, link);
            notification.setNotifiedAt(now);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> featured(String language, int limit) {
        return mapCards(repository.findFeatured(PageRequest.of(0, limit)), language);
    }

    /**
     * Cards for an already-loaded, caller-ordered product list — wishlist and "recently
     * viewed" both need exactly this: the same price/image/translation resolution the
     * catalog uses, applied to a small set of specific products rather than a query.
     */
    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> cardsFor(List<Product> products, String language) {
        return mapCards(products, language);
    }

    /**
     * Cards for a caller-supplied id list, in that exact order — "recently viewed" is a
     * client-side localStorage list, most-recent-first, and the whole point is showing it
     * back in that order. The {@code IN (...)} lookup itself makes no order guarantee, so
     * results are re-sorted against the input afterwards; an id that no longer resolves
     * (product deleted or unpublished since it was viewed) is silently dropped rather than
     * erroring the whole rail.
     */
    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> byIds(List<Long> ids, String language) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = repository.findPublishedCardsByIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<Product> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return mapCards(ordered, language);
    }

    /** Products currently on a campaign, newest campaign first. */
    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> discounted(String language, int limit) {
        List<Long> ids = discountResolver.discountedProductIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Long> limited = ids.size() > limit ? ids.subList(0, limit) : ids;
        List<Product> products = repository.findPublishedCardsByIds(limited);

        var context = discountResolver.context();
        Map<Long, String> imageUrls = imageUrls(products);
        String fallback = languageService.defaultLanguageCode();

        // Deepest discount first — that is what a "Kampaniya" rail should lead with.
        return products.stream()
                .map(product -> toCard(product, language, fallback, context.priceFor(product), imageUrls))
                .sorted(Comparator.comparingInt(
                        (ProductDtos.ProductCard card) -> card.price().savingPercentage()).reversed())
                .toList();
    }

    /**
     * Curated relations first; topped up with same-category products so the rail is
     * never half empty on a product nobody has linked yet.
     */
    @Transactional(readOnly = true)
    public List<ProductDtos.ProductCard> related(String slug, String language, int limit) {
        Product product = repository.findPublishedBySlug(slug)
                .orElseThrow(() -> ApiException.notFoundBySlug("Məhsul", slug));

        List<Product> related = new ArrayList<>(repository.findRelated(product.getId(), limit));

        if (related.size() < limit && product.getCategory() != null) {
            Set<Long> seen = related.stream().map(Product::getId).collect(Collectors.toSet());
            seen.add(product.getId());
            repository.findSameCategory(product.getCategory().getId(), product.getId(),
                            PageRequest.of(0, limit))
                    .stream()
                    .filter(candidate -> !seen.contains(candidate.getId()))
                    .limit(limit - related.size())
                    .forEach(related::add);
        }
        return mapCards(related, language);
    }

    // =======================================================================
    // Admin reads
    // =======================================================================

    @Transactional(readOnly = true)
    public Page<ProductDtos.ProductRow> adminSearch(ProductFilter filter, String language, Pageable pageable) {
        Collection<Long> categoryIds = resolveCategoryIds(filter);
        List<Long> discountedIds = Boolean.TRUE.equals(filter.discounted())
                ? discountResolver.discountedProductIds()
                : List.of();

        Page<Product> page = repository.findAll(
                ProductSpecifications.build(filter, language, categoryIds, discountedIds), pageable);

        var context = discountResolver.context();
        Map<Long, String> imageUrls = imageUrls(page.getContent());
        String fallback = languageService.defaultLanguageCode();

        List<ProductDtos.ProductRow> rows = page.getContent().stream()
                .map(product -> {
                    var translation = product.translation(language, fallback);
                    Category category = product.getCategory();
                    return new ProductDtos.ProductRow(
                            product.getId(), product.getSku(), product.getSlug(),
                            translation != null ? translation.getName() : product.getSku(),
                            imageUrls.get(product.getId()),
                            category == null ? null : category.getId(),
                            category == null ? null : categoryName(category, language, fallback),
                            product.getPrice(), context.priceFor(product), product.getCurrency(),
                            product.getStatus(), product.isFeatured(), product.isPurchasable(),
                            product.isTrackStock(), product.getStockQuantity(), product.stockStatus(),
                            product.getSortOrder(), product.getViewCount(), product.getUpdatedAt());
                })
                .toList();

        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse get(Long id, String language) {
        Product product = repository.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Məhsul", id));
        String fallback = languageService.defaultLanguageCode();

        Map<String, ProductDtos.TranslationDto> translations = new LinkedHashMap<>();
        product.getTranslations().forEach((code, translation) -> translations.put(code,
                new ProductDtos.TranslationDto(translation.getName(), translation.getShortDescription(),
                        translation.getDescription(), translation.getSeoTitle(),
                        translation.getSeoDescription())));

        List<ProductDtos.AttributeValueDto> attributes = product.getAttributeValues().stream()
                .map(value -> new ProductDtos.AttributeValueDto(
                        value.getAttribute().getId(),
                        value.getAttribute().getCode(),
                        value.getAttribute().name(language, fallback),
                        value.getOption() == null ? null : value.getOption().getId(),
                        value.getTextValue(), value.getNumberValue(), value.getBooleanValue()))
                .toList();

        return new ProductDtos.ProductResponse(
                product.getId(), product.getSku(), product.getBarcode(), product.getSlug(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getPrice(), product.getCompareAtPrice(), product.getCostPrice(),
                product.getCurrency(), product.getTaxRate(), product.isShowPrice(),
                product.isPurchasable(), product.isTrackStock(), product.getStockQuantity(),
                product.getLowStockThreshold(), product.isAllowBackorder(),
                product.getMinOrderQuantity(), product.getMaxOrderQuantity(), product.getWeightGrams(),
                product.isFeatured(), product.isNew(), product.getStatus(), product.getSortOrder(),
                product.getViewCount(), product.getPublishedAt(),
                discountResolver.resolve(product),
                translations, toImageDtos(product), toDocumentDtos(product), attributes,
                List.of());
    }

    // =======================================================================
    // Admin writes
    // =======================================================================

    @Transactional
    public ProductDtos.ProductResponse create(ProductDtos.SaveRequest request, String language) {
        requireDefaultTranslation(request.translations());

        if (repository.existsBySku(request.sku(), null)) {
            throw new ApiException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        Product product = new Product();
        product.setSku(request.sku().trim());
        product.setSlug(resolveSlug(request, null));
        applyScalars(product, request);
        applyTranslations(product, request.translations());
        applyAttributes(product, request.attributes());

        Product saved = repository.save(product);
        log.info("Created product {} ({})", saved.getId(), saved.getSku());
        return get(saved.getId(), language);
    }

    @Transactional
    public ProductDtos.ProductResponse update(Long id, ProductDtos.SaveRequest request, String language) {
        requireDefaultTranslation(request.translations());
        Product product = loadDetail(id);

        if (repository.existsBySku(request.sku(), id)) {
            throw new ApiException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        boolean wasOutOfStock = product.stockStatus() == StockStatus.OUT_OF_STOCK;

        product.setSku(request.sku().trim());
        product.setSlug(resolveSlug(request, product));
        applyScalars(product, request);
        applyTranslations(product, request.translations());
        applyAttributes(product, request.attributes());

        if (wasOutOfStock && product.stockStatus() != StockStatus.OUT_OF_STOCK) {
            notifyBackInStock(product);
        }

        return get(id, language);
    }

    @Transactional
    public void changeStatus(Long id, ContentStatus status) {
        Product product = load(id);
        product.setStatus(status);
        if (status == ContentStatus.ACTIVE && product.getPublishedAt() == null) {
            product.setPublishedAt(OffsetDateTime.now());
        }
    }

    @Transactional
    public void changeFeatured(Long id, boolean featured) {
        load(id).setFeatured(featured);
    }

    @Transactional
    public void adjustStock(Long id, int quantity) {
        Product product = load(id);
        boolean wasOutOfStock = product.stockStatus() == StockStatus.OUT_OF_STOCK;
        product.setStockQuantity(Math.max(quantity, 0));
        if (wasOutOfStock && product.stockStatus() != StockStatus.OUT_OF_STOCK) {
            notifyBackInStock(product);
        }
    }

    @Transactional
    public void delete(Long id) {
        Product product = load(id);
        product.softDelete();
        product.setStatus(ContentStatus.ARCHIVED);
        log.info("Soft deleted product {} ({})", id, product.getSku());
    }

    /**
     * Copies a product into a new DRAFT. Images and documents are shared (the media rows
     * are reused, not the files), translations and specifications are cloned.
     */
    @Transactional
    public ProductDtos.ProductResponse duplicate(Long id, String language) {
        Product source = loadDetail(id);

        Product copy = new Product();
        copy.setSku(uniqueSku(source.getSku()));
        copy.setSlug(SlugUtil.uniqueSlug(source.getSlug() + "-kopya",
                repository::existsBySlugAndDeletedAtIsNull));
        copy.setCategory(source.getCategory());
        copy.setPrice(source.getPrice());
        copy.setCompareAtPrice(source.getCompareAtPrice());
        copy.setCostPrice(source.getCostPrice());
        copy.setCurrency(source.getCurrency());
        copy.setTaxRate(source.getTaxRate());
        copy.setShowPrice(source.isShowPrice());
        copy.setPurchasable(source.isPurchasable());
        copy.setTrackStock(source.isTrackStock());
        copy.setStockQuantity(0);
        copy.setLowStockThreshold(source.getLowStockThreshold());
        copy.setAllowBackorder(source.isAllowBackorder());
        copy.setMinOrderQuantity(source.getMinOrderQuantity());
        copy.setMaxOrderQuantity(source.getMaxOrderQuantity());
        copy.setWeightGrams(source.getWeightGrams());
        copy.setFeatured(false);
        // A copy always starts as a draft: publishing it is an explicit decision.
        copy.setStatus(ContentStatus.DRAFT);
        copy.setSortOrder(source.getSortOrder());

        source.getTranslations().forEach((code, translation) -> {
            ProductTranslation clone = ProductTranslation.builder()
                    .name(translation.getName() + " (kopya)")
                    .shortDescription(translation.getShortDescription())
                    .description(translation.getDescription())
                    .seoTitle(translation.getSeoTitle())
                    .seoDescription(translation.getSeoDescription())
                    .build();
            copy.putTranslation(code, clone);
        });

        source.getImages().forEach(image -> copy.getImages().add(ProductImage.builder()
                .product(copy).media(image.getMedia())
                .main(image.isMain()).sortOrder(image.getSortOrder()).build()));

        source.getDocuments().forEach(document -> copy.getDocuments().add(ProductDocument.builder()
                .product(copy).media(document.getMedia()).title(document.getTitle())
                .documentType(document.getDocumentType()).sortOrder(document.getSortOrder()).build()));

        source.getAttributeValues().forEach(value -> copy.getAttributeValues().add(
                ProductAttributeValue.builder()
                        .product(copy).attribute(value.getAttribute()).option(value.getOption())
                        .textValue(value.getTextValue()).numberValue(value.getNumberValue())
                        .booleanValue(value.getBooleanValue()).sortOrder(value.getSortOrder()).build()));

        Product saved = repository.save(copy);
        return get(saved.getId(), language);
    }

    // --- images -----------------------------------------------------------

    @Transactional
    public List<ProductDtos.ImageDto> addImage(Long productId, ProductDtos.AddImageRequest request) {
        Product product = loadDetail(productId);
        MediaFile media = mediaService.require(request.mediaId());

        boolean alreadyLinked = product.getImages().stream()
                .anyMatch(image -> image.getMedia().getId().equals(media.getId()));
        if (alreadyLinked) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "Bu şəkil artıq əlavə edilib");
        }

        boolean makeMain = Boolean.TRUE.equals(request.main()) || product.getImages().isEmpty();
        if (makeMain) {
            product.getImages().forEach(image -> image.setMain(false));
        }

        product.getImages().add(ProductImage.builder()
                .product(product).media(media).main(makeMain)
                .sortOrder(request.sortOrder() == null ? product.getImages().size() : request.sortOrder())
                .build());

        return toImageDtos(product);
    }

    @Transactional
    public List<ProductDtos.ImageDto> setMainImage(Long productId, Long imageId) {
        Product product = loadDetail(productId);
        ProductImage target = product.getImages().stream()
                .filter(image -> image.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Şəkil", imageId));

        product.getImages().forEach(image -> image.setMain(false));
        target.setMain(true);
        return toImageDtos(product);
    }

    @Transactional
    public List<ProductDtos.ImageDto> reorderImages(Long productId, ProductDtos.ReorderRequest request) {
        Product product = loadDetail(productId);
        Map<Long, Integer> order = request.items().stream()
                .collect(Collectors.toMap(ProductDtos.ReorderItem::id, ProductDtos.ReorderItem::sortOrder));
        product.getImages().forEach(image ->
                image.setSortOrder(order.getOrDefault(image.getId(), image.getSortOrder())));
        product.getImages().sort(Comparator.comparingInt(ProductImage::getSortOrder));
        return toImageDtos(product);
    }

    @Transactional
    public List<ProductDtos.ImageDto> removeImage(Long productId, Long imageId) {
        Product product = loadDetail(productId);
        boolean wasMain = product.getImages().stream()
                .filter(image -> image.getId().equals(imageId))
                .anyMatch(ProductImage::isMain);

        product.getImages().removeIf(image -> image.getId().equals(imageId));

        // Removing the main image promotes the next one rather than leaving the product
        // with no primary image at all.
        if (wasMain && !product.getImages().isEmpty()) {
            product.getImages().get(0).setMain(true);
        }
        return toImageDtos(product);
    }

    // --- documents --------------------------------------------------------

    @Transactional
    public List<ProductDtos.DocumentDto> addDocument(Long productId, ProductDtos.AddDocumentRequest request) {
        Product product = loadDetail(productId);
        MediaFile media = mediaService.require(request.mediaId());

        product.getDocuments().add(ProductDocument.builder()
                .product(product).media(media)
                .title(sanitizer.stripTags(request.title()))
                .documentType(request.documentType() == null
                        ? ProductDocument.DocumentType.DATASHEET : request.documentType())
                .sortOrder(request.sortOrder() == null ? product.getDocuments().size() : request.sortOrder())
                .build());

        return toDocumentDtos(product);
    }

    @Transactional
    public List<ProductDtos.DocumentDto> removeDocument(Long productId, Long documentId) {
        Product product = loadDetail(productId);
        product.getDocuments().removeIf(document -> document.getId().equals(documentId));
        return toDocumentDtos(product);
    }

    // =======================================================================
    // Mapping
    // =======================================================================

    private Page<ProductDtos.ProductCard> mapCards(Page<Product> page, String language) {
        List<ProductDtos.ProductCard> cards = mapCards(page.getContent(), language);
        return new PageImpl<>(cards, page.getPageable(), page.getTotalElements());
    }

    private List<ProductDtos.ProductCard> mapCards(List<Product> products, String language) {
        if (products.isEmpty()) {
            return List.of();
        }
        var context = discountResolver.context();
        Map<Long, String> imageUrls = imageUrls(products);
        String fallback = languageService.defaultLanguageCode();

        return products.stream()
                .map(product -> toCard(product, language, fallback, context.priceFor(product), imageUrls))
                .toList();
    }

    private ProductDtos.ProductCard toCard(Product product, String language, String fallback,
                                           PriceInfo price, Map<Long, String> imageUrls) {
        ProductTranslation translation = product.translation(language, fallback);
        Category category = product.getCategory();
        ProductImage mainImage = product.mainImage();

        return new ProductDtos.ProductCard(
                product.getId(), product.getSlug(), product.getSku(),
                translation != null ? translation.getName() : product.getSku(),
                translation != null ? translation.getShortDescription() : null,
                imageUrls.get(product.getId()),
                mainImage != null ? mainImage.getMedia().getAltText() : null,
                category == null ? null : category.getId(),
                category == null ? null : category.getSlug(),
                category == null ? null : categoryName(category, language, fallback),
                price, product.isShowPrice(), product.isFeatured(), product.isNew(),
                product.isAvailableForPurchase(), product.stockStatus(),
                product.isTrackStock() ? product.getStockQuantity() : null);
    }

    private ProductDtos.ProductDetail toDetail(Product product, String language) {
        String fallback = languageService.defaultLanguageCode();
        ProductTranslation translation = product.translation(language, fallback);
        Category category = product.getCategory();

        List<ProductVariant> variants = variantRepository
                .findAllByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .filter(ProductVariant::isActive)
                .toList();

        List<Object[]> ratingRows = reviewRepository.ratingSummary(product.getId());
        Object[] ratingRow = ratingRows.isEmpty() ? new Object[]{0, 0L} : ratingRows.get(0);

        return new ProductDtos.ProductDetail(
                product.getId(), product.getSlug(), product.getSku(), product.getBarcode(),
                translation != null ? translation.getName() : product.getSku(),
                translation != null ? translation.getShortDescription() : null,
                translation != null ? translation.getDescription() : null,
                translation != null ? translation.getSeoTitle() : null,
                translation != null ? translation.getSeoDescription() : null,
                category == null ? null : category.getId(),
                category == null ? null : category.getSlug(),
                category == null ? null : categoryName(category, language, fallback),
                discountResolver.resolve(product), product.isShowPrice(), product.getCurrency(),
                product.getCompareAtPrice(), product.isFeatured(), product.isNew(),
                product.isAvailableForPurchase(), product.stockStatus(),
                product.isTrackStock() ? product.getStockQuantity() : null,
                product.getMinOrderQuantity(), product.getMaxOrderQuantity(), product.getWeightGrams(),
                toImageDtos(product), toDocumentDtos(product),
                buildSpecifications(product, language, fallback),
                product.getPublishedAt(),
                toVariantDtos(variants, product),
                buildVariantAttributes(variants, language, fallback),
                ratingAverage(ratingRow), ratingCount(ratingRow));
    }

    private double ratingAverage(Object[] row) {
        return row[0] instanceof Number number ? Math.round(number.doubleValue() * 10) / 10.0 : 0;
    }

    private long ratingCount(Object[] row) {
        return row[1] instanceof Number number ? number.longValue() : 0;
    }

    // =======================================================================
    // Variants
    // =======================================================================

    private List<ProductDtos.VariantDto> toVariantDtos(List<ProductVariant> variants, Product product) {
        return variants.stream()
                .map(variant -> new ProductDtos.VariantDto(
                        variant.getId(), variant.getSku(),
                        variant.getPriceOverride() != null ? variant.getPriceOverride() : product.getPrice(),
                        variant.getPriceOverride() != null,
                        variant.stockStatus(product.getLowStockThreshold()),
                        variant.isTrackStock() ? variant.getStockQuantity() : null,
                        variant.getImage() != null ? variant.getImage().getPublicUrl() : null,
                        variant.isDefault(),
                        variant.getOptions().stream().map(AttributeOption::getId).sorted().toList()))
                .toList();
    }

    /** Groups every option that actually appears on a variant, by its attribute — the axes a shopper picks. */
    private List<ProductDtos.VariantAttributeDto> buildVariantAttributes(
            List<ProductVariant> variants, String language, String fallback) {
        Map<Long, Attribute> attributesById = new LinkedHashMap<>();
        Map<Long, List<AttributeOption>> optionsByAttribute = new LinkedHashMap<>();

        for (ProductVariant variant : variants) {
            for (AttributeOption option : variant.getOptions()) {
                Attribute attribute = option.getAttribute();
                attributesById.putIfAbsent(attribute.getId(), attribute);
                List<AttributeOption> options = optionsByAttribute.computeIfAbsent(
                        attribute.getId(), key -> new ArrayList<>());
                if (options.stream().noneMatch(existing -> existing.getId().equals(option.getId()))) {
                    options.add(option);
                }
            }
        }

        return attributesById.values().stream()
                .sorted(Comparator.comparingInt(Attribute::getSortOrder))
                .map(attribute -> new ProductDtos.VariantAttributeDto(
                        attribute.getId(), attribute.getCode(), attribute.name(language, fallback),
                        optionsByAttribute.get(attribute.getId()).stream()
                                .sorted(Comparator.comparingInt(AttributeOption::getSortOrder))
                                .map(option -> new ProductDtos.VariantOptionDto(
                                        option.getId(), option.getValue(),
                                        option.label(language, fallback), option.getColorHex()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.VariantAdminDto> listVariants(Long productId) {
        load(productId);
        return variantRepository.findAllByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(this::toVariantAdminDto)
                .toList();
    }

    @Transactional
    public List<ProductDtos.VariantAdminDto> createVariant(Long productId, ProductDtos.SaveVariantRequest request) {
        Product product = load(productId);
        validateVariantSku(request.sku(), null);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(request.sku())
                .priceOverride(request.priceOverride())
                .trackStock(request.trackStock() == null || request.trackStock())
                .stockQuantity(request.stockQuantity() == null ? 0 : request.stockQuantity())
                .allowBackorder(Boolean.TRUE.equals(request.allowBackorder()))
                .image(request.imageMediaId() == null ? null : mediaService.require(request.imageMediaId()))
                .sortOrder(request.sortOrder() == null
                        ? (int) variantRepository.countByProductId(productId) : request.sortOrder())
                .isDefault(Boolean.TRUE.equals(request.isDefault()))
                .active(request.active() == null || request.active())
                .options(resolveVariantOptions(request.optionIds()))
                .build();

        variantRepository.save(variant);
        return listVariants(productId);
    }

    @Transactional
    public List<ProductDtos.VariantAdminDto> updateVariant(
            Long productId, Long variantId, ProductDtos.SaveVariantRequest request) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> ApiException.notFound("Variant", variantId));
        validateVariantSku(request.sku(), variantId);

        variant.setSku(request.sku());
        variant.setPriceOverride(request.priceOverride());
        variant.setTrackStock(request.trackStock() == null || request.trackStock());
        variant.setStockQuantity(request.stockQuantity() == null ? variant.getStockQuantity() : request.stockQuantity());
        variant.setAllowBackorder(Boolean.TRUE.equals(request.allowBackorder()));
        variant.setImage(request.imageMediaId() == null ? null : mediaService.require(request.imageMediaId()));
        if (request.sortOrder() != null) {
            variant.setSortOrder(request.sortOrder());
        }
        variant.setDefault(Boolean.TRUE.equals(request.isDefault()));
        variant.setActive(request.active() == null || request.active());
        variant.setOptions(resolveVariantOptions(request.optionIds()));

        return listVariants(productId);
    }

    @Transactional
    public List<ProductDtos.VariantAdminDto> deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> ApiException.notFound("Variant", variantId));
        variantRepository.delete(variant);
        return listVariants(productId);
    }

    private Set<AttributeOption> resolveVariantOptions(List<Long> optionIds) {
        List<AttributeOption> found = attributeOptionRepository.findAllDetailByIds(optionIds);
        if (found.size() != new HashSet<>(optionIds).size()) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "optionIds", "Seçimlərdən biri tapılmadı");
        }
        // At most one option per attribute — a variant cannot be both "Qırmızı" and "Yaşıl".
        Map<Long, Long> perAttribute = found.stream()
                .collect(Collectors.groupingBy(option -> option.getAttribute().getId(), Collectors.counting()));
        if (perAttribute.values().stream().anyMatch(count -> count > 1)) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "optionIds",
                    "Hər attributdan yalnız bir seçim ola bilər");
        }
        return new LinkedHashSet<>(found);
    }

    private void validateVariantSku(String sku, Long excludeId) {
        boolean taken = excludeId == null
                ? variantRepository.existsBySku(sku)
                : variantRepository.existsBySkuAndIdNot(sku, excludeId);
        if (taken) {
            throw new ApiException(ErrorCode.SKU_ALREADY_EXISTS);
        }
    }

    private ProductDtos.VariantAdminDto toVariantAdminDto(ProductVariant variant) {
        String fallback = languageService.defaultLanguageCode();
        return new ProductDtos.VariantAdminDto(
                variant.getId(), variant.getSku(), variant.getPriceOverride(),
                variant.isTrackStock(), variant.getStockQuantity(), variant.isAllowBackorder(),
                variant.getImage() != null ? variant.getImage().getId() : null,
                variant.getImage() != null ? variant.getImage().getPublicUrl() : null,
                variant.getSortOrder(), variant.isDefault(), variant.isActive(),
                variant.getOptions().stream().map(AttributeOption::getId).sorted().toList(),
                variant.optionLabel(fallback, fallback));
    }

    /** Groups specification lines the way the admin organised the attribute groups. */
    private List<ProductDtos.SpecificationGroup> buildSpecifications(Product product, String language,
                                                                     String fallback) {
        Map<String, List<ProductDtos.SpecificationLine>> grouped = new LinkedHashMap<>();
        Map<String, String> groupNames = new LinkedHashMap<>();

        product.getAttributeValues().stream()
                .sorted(Comparator.comparingInt(value -> value.getAttribute().getSortOrder()))
                .forEach(value -> {
                    Attribute attribute = value.getAttribute();
                    if (attribute.getStatus() != ContentStatus.ACTIVE) {
                        return;
                    }
                    String groupCode = attribute.getGroup() == null
                            ? "general" : attribute.getGroup().getCode();
                    groupNames.putIfAbsent(groupCode, attribute.getGroup() == null
                            ? "Ümumi" : attribute.getGroup().name(language, fallback));

                    grouped.computeIfAbsent(groupCode, key -> new ArrayList<>())
                            .add(new ProductDtos.SpecificationLine(
                                    attribute.getCode(),
                                    attribute.name(language, fallback),
                                    value.displayValue(language, fallback),
                                    attribute.getUnit()));
                });

        return grouped.entrySet().stream()
                .map(entry -> new ProductDtos.SpecificationGroup(
                        entry.getKey(), groupNames.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private List<ProductDtos.ImageDto> toImageDtos(Product product) {
        return product.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSortOrder))
                .map(image -> new ProductDtos.ImageDto(
                        image.getId(), image.getMedia().getId(), image.getMedia().getPublicUrl(),
                        image.getMedia().getAltText(), image.isMain(), image.getSortOrder()))
                .toList();
    }

    private List<ProductDtos.DocumentDto> toDocumentDtos(Product product) {
        return product.getDocuments().stream()
                .sorted(Comparator.comparingInt(ProductDocument::getSortOrder))
                .map(document -> new ProductDtos.DocumentDto(
                        document.getId(), document.getMedia().getId(), document.getMedia().getPublicUrl(),
                        document.getTitle(), document.getDocumentType(), document.getMedia().getFileSize()))
                .toList();
    }

    /** One lookup for a whole page of cards instead of touching every lazy image collection. */
    private Map<Long, String> imageUrls(Collection<Product> products) {
        return products.stream()
                .map(product -> Map.entry(product.getId(), Optional.ofNullable(product.mainImage())))
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().get().getMedia().getPublicUrl(),
                        (first, second) -> first));
    }

    private String categoryName(Category category, String language, String fallback) {
        var translation = category.translation(language, fallback);
        return translation != null ? translation.getName() : category.getSlug();
    }

    // =======================================================================
    // Write helpers
    // =======================================================================

    private void applyScalars(Product product, ProductDtos.SaveRequest request) {
        product.setBarcode(request.barcode());
        product.setCategory(request.categoryId() == null ? null
                : categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .orElseThrow(() -> ApiException.notFound("Kateqoriya", request.categoryId())));

        product.setPrice(request.price());
        product.setCompareAtPrice(request.compareAtPrice());
        product.setCostPrice(request.costPrice());
        if (request.currency() != null && !request.currency().isBlank()) {
            product.setCurrency(request.currency());
        }
        product.setTaxRate(request.taxRate() == null ? BigDecimal.ZERO : request.taxRate());
        product.setShowPrice(request.showPrice() == null || request.showPrice());

        product.setPurchasable(request.purchasable() == null || request.purchasable());
        product.setTrackStock(Boolean.TRUE.equals(request.trackStock()));
        product.setStockQuantity(request.stockQuantity() == null ? 0 : request.stockQuantity());
        product.setLowStockThreshold(request.lowStockThreshold() == null ? 5 : request.lowStockThreshold());
        product.setAllowBackorder(Boolean.TRUE.equals(request.allowBackorder()));
        product.setMinOrderQuantity(request.minOrderQuantity() == null ? 1 : request.minOrderQuantity());
        product.setMaxOrderQuantity(request.maxOrderQuantity());
        product.setWeightGrams(request.weightGrams());

        if (product.getMaxOrderQuantity() != null
                && product.getMaxOrderQuantity() < product.getMinOrderQuantity()) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "maxOrderQuantity",
                    "Maksimum say minimumdan kiçik ola bilməz");
        }

        product.setFeatured(Boolean.TRUE.equals(request.featured()));
        product.setNew(Boolean.TRUE.equals(request.isNew()));

        ContentStatus status = request.status() == null ? ContentStatus.DRAFT : request.status();
        product.setStatus(status);
        if (status == ContentStatus.ACTIVE && product.getPublishedAt() == null) {
            product.setPublishedAt(OffsetDateTime.now());
        }
        if (request.sortOrder() != null) {
            product.setSortOrder(request.sortOrder());
        }
    }

    private void applyTranslations(Product product, Map<String, ProductDtos.TranslationDto> translations) {
        Set<String> active = Set.copyOf(languageService.activeLanguageCodes());
        product.getTranslations().keySet().removeIf(code -> !translations.containsKey(code));

        translations.forEach((code, dto) -> {
            if (!active.contains(code)) {
                throw new ApiException(ErrorCode.LANGUAGE_NOT_ACTIVE, "Dil aktiv deyil: " + code);
            }
            ProductTranslation translation = product.getTranslations()
                    .getOrDefault(code, new ProductTranslation());
            translation.setName(sanitizer.stripTags(dto.name()).trim());
            translation.setShortDescription(sanitizer.stripTags(dto.shortDescription()));
            // Only the long description accepts markup, and only after sanitising.
            translation.setDescription(sanitizer.sanitizeRichText(dto.description()));
            translation.setSeoTitle(sanitizer.stripTags(dto.seoTitle()));
            translation.setSeoDescription(sanitizer.stripTags(dto.seoDescription()));
            product.putTranslation(code, translation);
        });
    }

    /**
     * Replaces the whole specification set in one shot, as documented in the API contract.
     * Each value is checked against its attribute's data type so a NUMBER attribute can
     * never end up holding free text.
     */
    private void applyAttributes(Product product, List<ProductDtos.AttributeValueDto> values) {
        product.getAttributeValues().clear();
        if (values == null || values.isEmpty()) {
            return;
        }

        Map<Long, Attribute> attributes = attributeRepository
                .findAllDetailByIds(values.stream()
                        .map(ProductDtos.AttributeValueDto::attributeId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Attribute::getId, Function.identity()));

        Map<Long, AttributeOption> options = attributeOptionRepository
                .findAllDetailByIds(values.stream()
                        .map(ProductDtos.AttributeValueDto::optionId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(AttributeOption::getId, Function.identity()));

        int sortOrder = 0;
        for (ProductDtos.AttributeValueDto dto : values) {
            Attribute attribute = attributes.get(dto.attributeId());
            if (attribute == null) {
                throw ApiException.notFound("Attribut", dto.attributeId());
            }

            ProductAttributeValue value = ProductAttributeValue.builder()
                    .product(product).attribute(attribute).sortOrder(sortOrder++).build();

            switch (attribute.getDataType()) {
                case TEXT -> {
                    requirePresent(dto.textValue(), attribute, "textValue");
                    value.setTextValue(sanitizer.stripTags(dto.textValue()));
                }
                case NUMBER -> {
                    if (dto.numberValue() == null) {
                        throw typeMismatch(attribute, "numberValue");
                    }
                    value.setNumberValue(dto.numberValue());
                }
                case BOOLEAN -> {
                    if (dto.booleanValue() == null) {
                        throw typeMismatch(attribute, "booleanValue");
                    }
                    value.setBooleanValue(dto.booleanValue());
                }
                case SELECT, MULTI_SELECT -> {
                    if (dto.optionId() == null) {
                        throw typeMismatch(attribute, "optionId");
                    }
                    AttributeOption option = options.get(dto.optionId());
                    if (option == null || !option.getAttribute().getId().equals(attribute.getId())) {
                        throw ApiException.field(ErrorCode.ATTRIBUTE_VALUE_TYPE_MISMATCH, "optionId",
                                "Seçim bu attributa aid deyil");
                    }
                    value.setOption(option);
                }
            }
            product.getAttributeValues().add(value);
        }

        // MULTI_SELECT repeats rows; every other type must appear at most once.
        Map<Long, Long> counts = product.getAttributeValues().stream()
                .collect(Collectors.groupingBy(value -> value.getAttribute().getId(), Collectors.counting()));
        counts.forEach((attributeId, count) -> {
            Attribute attribute = attributes.get(attributeId);
            if (count > 1 && attribute.getDataType() != AttributeDataType.MULTI_SELECT) {
                throw ApiException.field(ErrorCode.ATTRIBUTE_VALUE_TYPE_MISMATCH, "attributes",
                        "'%s' attributu yalnız bir dəyər ala bilər".formatted(attribute.getCode()));
            }
        });
    }

    private void requirePresent(String value, Attribute attribute, String field) {
        if (value == null || value.isBlank()) {
            throw typeMismatch(attribute, field);
        }
    }

    private ApiException typeMismatch(Attribute attribute, String field) {
        return ApiException.field(ErrorCode.ATTRIBUTE_VALUE_TYPE_MISMATCH, field,
                "'%s' attributu %s tipindədir".formatted(attribute.getCode(), attribute.getDataType()));
    }

    private void requireDefaultTranslation(Map<String, ProductDtos.TranslationDto> translations) {
        String defaultLanguage = languageService.defaultLanguageCode();
        var translation = translations == null ? null : translations.get(defaultLanguage);
        if (translation == null || translation.name() == null || translation.name().isBlank()) {
            throw ApiException.field(ErrorCode.DEFAULT_LANGUAGE_REQUIRED,
                    "translations." + defaultLanguage + ".name",
                    "Əsas dil (%s) üçün ad mütləqdir".formatted(defaultLanguage));
        }
    }

    private String resolveSlug(ProductDtos.SaveRequest request, Product existing) {
        String requested = request.slug();
        if (requested == null || requested.isBlank()) {
            if (existing != null) {
                return existing.getSlug();
            }
            String source = request.translations().get(languageService.defaultLanguageCode()).name();
            return SlugUtil.uniqueSlug(source, repository::existsBySlugAndDeletedAtIsNull);
        }
        String slug = SlugUtil.slugify(requested);
        if (!SlugUtil.isValidSlug(slug)) {
            throw ApiException.field(ErrorCode.VALIDATION_ERROR, "slug", "Slug formatı yanlışdır");
        }
        if (repository.existsBySlug(slug, existing == null ? null : existing.getId())) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    private String uniqueSku(String base) {
        for (int suffix = 1; suffix < 1000; suffix++) {
            String candidate = base + "-" + suffix;
            if (!repository.existsBySku(candidate, null)) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }

    private Collection<Long> resolveCategoryIds(ProductFilter filter) {
        Long categoryId = filter.categoryId();
        if (categoryId == null && filter.categorySlug() != null && !filter.categorySlug().isBlank()) {
            categoryId = categoryRepository.findPublishedBySlug(filter.categorySlug())
                    .map(Category::getId)
                    .orElseThrow(() -> ApiException.notFoundBySlug("Kateqoriya", filter.categorySlug()));
        }
        // A category filter always includes descendants: picking "Mebel" must show the
        // chairs inside it, which is what a shopper expects.
        return categoryId == null ? List.of() : categoryRepository.findSubtreeIds(categoryId);
    }

    private Product load(Long id) {
        return repository.findAliveById(id).orElseThrow(() -> ApiException.notFound("Məhsul", id));
    }

    private Product loadDetail(Long id) {
        return repository.findDetailById(id).orElseThrow(() -> ApiException.notFound("Məhsul", id));
    }
}
